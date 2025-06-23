/* code-guru-scanner-worker
 * Copyright (C) 2025 Srijan Singh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details:
 *     https://www.gnu.org/licenses/gpl-3.0.txt
 */
package code.guru.action;

import code.guru.chunks.ChunkData;
import code.guru.ipc.GPRCImplementation;
import code.guru.ipc.IngestionIPC;
import code.guru.structure.ProjectStructure;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ScanProjectAction extends AnAction {

    private static final String SCANNER_WORKER = "Scanner Worker";

    private static final Logger log = Logger.getInstance(ScanProjectAction.class);

    // Create a notification group for our plugin
    private static final NotificationGroup NOTIFICATION_GROUP =
            NotificationGroupManager.getInstance().getNotificationGroup(SCANNER_WORKER);

    IngestionIPC ingestionIPC = new GPRCImplementation();

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            log.warn("No project found in context.");
            Messages.showErrorDialog("No project found in context.", SCANNER_WORKER);
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning Java Files", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
                performChunkScanning(project, indicator);
                }
            });
        }

    private void performScan(Project project, ProgressIndicator indicator) {
        log.info("Starting Java file scan for project: %s => %s".formatted(project.getName(), indicator.getFraction()));

        try {
            ProjectStructure structure = new ProjectStructure(project);
            List<PsiJavaFile> javaFiles = structure.getAllJavaPsiFiles();

            // Indicator
            indicator.setIndeterminate(false);
            indicator.setFraction(0.0);

            String info = "Found %s Java files in project %s".formatted(javaFiles.size(), project.getName());
            log.info(info);
            // Show a notification to the user
            showNotification(project, info, NotificationType.INFORMATION);

            int totalClasses = 0;
            int totalMethods = 0;

            for (int i = 0; i < javaFiles.size(); i++) {
                PsiJavaFile file = javaFiles.get(i);

                double progress = (double) i / javaFiles.size();
                indicator.setFraction(progress);
                indicator.setFraction(progress);

                if (file.getVirtualFile() != null) {
                    log.info("File: %s".formatted(file.getVirtualFile().getPath()));
                } else {
                    log.info("File: %s (no virtual file)".formatted(file.getName()));
                }

                PsiClass[] classes = file.getClasses();
                if (classes.length == 0) {
                    log.info("  No classes found in this file.");
                } else {
                    totalClasses += classes.length;

                    for (PsiClass psiClass : classes) {
                        String className = psiClass.getName();
                        String qualifiedName = psiClass.getQualifiedName();

                        if (className == null) {
                            log.warn("Found class with null name, skipping");
                            continue;
                        }

                        log.info("  Class: %s (Qualified Name: %s)".formatted(className, qualifiedName));

                        PsiMethod[] methods = psiClass.getMethods();
                        if (methods.length == 0) {
                            log.info("    No methods found.");
                        } else {
                            totalMethods += methods.length;

                            for (PsiMethod method : methods) {
                                String methodName = method.getName();
                                String parameters = method.getParameterList().getText();
                                log.info("    Method: %s %s".formatted(methodName, parameters));
                            }
                        }
                    }
                }
                log.info("Progress: %s".formatted(indicator.getFraction()));
            }

            indicator.setFraction(1.0);
            indicator.setText("Scan completed");

            // Show summary
            String summary = String.format("%s %s: %d files, %d classes, %d methods",
                    indicator.getText(), indicator.getFraction(), javaFiles.size(), totalClasses, totalMethods);
            log.info(summary);
            showNotification(project, summary, NotificationType.INFORMATION);

            // Also show a dialog with the summary
            Messages.showInfoMessage(project, summary, "Scanner Worker - Scan Results");

        } catch (Exception e) {
            log.error("Error during Java file scanning", e);
            showNotification(project, "Error during scanning: " + e.getMessage(), NotificationType.ERROR);
            Messages.showErrorDialog(project, "Error during scanning: " + e.getMessage(), "Scanner Worker");
        }
    }

    private void performChunkScanning(Project project, ProgressIndicator indicator) {
        List<ChunkData> chunks = new ArrayList<>();

        try {
            AtomicInteger totalClasses = new AtomicInteger();
            AtomicInteger totalMethods = new AtomicInteger();
            List<PsiJavaFile> javaFiles = ApplicationManager.getApplication().runReadAction(
                (Computable<List<PsiJavaFile>>) () -> {
                    ProjectStructure structure = new ProjectStructure(project);
                    return structure.getAllJavaPsiFiles();
                }
            );

            // Show initial info
            String info = "Found %s Java files in project %s".formatted(javaFiles.size(), project.getName());
            log.info(info);
            showNotification(project, info, NotificationType.INFORMATION);

            indicator.setIndeterminate(false);
            indicator.setFraction(0.0);

            // Wrap PSI read loop
            ApplicationManager.getApplication().runReadAction(() -> {
                for (int i = 0; i < javaFiles.size(); i++) {
                    if (indicator.isCanceled()) break;

                    PsiJavaFile file = javaFiles.get(i);
                    double progress = (double) i / javaFiles.size();
                    indicator.setFraction(progress);

                    VirtualFile vf = file.getVirtualFile();
                    log.info("File: %s".formatted(vf != null ? vf.getPath() : file.getName() + " (no virtual file)"));

                    PsiClass[] classes = file.getClasses();
                    if (classes.length == 0) {
                        log.info("  No classes found in this file.");
                    } else {
                        totalClasses.addAndGet(classes.length);

                        for (PsiClass psiClass : classes) {
                            String className = psiClass.getQualifiedName();
                            if (className == null) {
                                log.warn("Found class with null name, skipping");
                                continue;
                            }

                            List<String> classAttributes = Arrays.stream(psiClass.getFields())
                                    .map(field -> field.getName() + ":" + field.getType().getPresentableText())
                                    .toList();

                            log.info("  Class Qualified Name: %s".formatted(className));

                            PsiMethod[] methods = psiClass.getMethods();
                            if (methods.length == 0) {
                                log.info("    No methods found.");
                            } else {
                                totalMethods.addAndGet(methods.length);

                                for (PsiMethod method : methods) {
                                    if (indicator.isCanceled()) break;

                                    String methodName = method.getName();
                                    String returnType = method.getReturnType() != null
                                            ? method.getReturnType().getPresentableText()
                                            : "void";
                                    List<String> parameters = Arrays.stream(method.getParameterList().getParameters())
                                            .map(p -> p.getType().getPresentableText() + " " + p.getName())
                                            .collect(Collectors.toList());

                                    List<String> dependencies = new ArrayList<>();
                                    List<String> calledBy = new ArrayList<>(); // TODO: Advance Impl

                                    method.accept(new JavaRecursiveElementVisitor() {
                                        @Override
                                        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                                            PsiMethod resolvedMethod = expression.resolveMethod();
                                            if (resolvedMethod != null) {
                                                PsiClass containingClass = resolvedMethod.getContainingClass();
                                                if (containingClass != null) {
                                                    String fqMethod = containingClass.getQualifiedName() + "." + resolvedMethod.getName();
                                                    dependencies.add(fqMethod);
                                                }
                                            }
                                            super.visitMethodCallExpression(expression);
                                        }

                                        @Override
                                        public void visitReferenceExpression(PsiReferenceExpression expression) {
                                            PsiElement resolved = expression.resolve();
                                            if (resolved instanceof PsiField field) {
                                                PsiClass containingClass = field.getContainingClass();
                                                if (containingClass != null) {
                                                    String fqField = containingClass.getQualifiedName() + "." + field.getName();
                                                    dependencies.add(fqField);
                                                }
                                            }
                                            super.visitReferenceExpression(expression);
                                        }
                                    });

                                    String methodCode = method.getText();

                                    ChunkData chunk = ChunkData.builder()
                                            .className(className)
                                            .methodName(methodName)
                                            .returnType(returnType)
                                            .parameters(parameters)
                                            .classAttributes(classAttributes)
                                            .calledBy(calledBy)
                                            .dependencies(dependencies)
                                            .methodCode(methodCode)
                                            .build();

                                    chunks.add(chunk);
                                }
                            }
                        }
                    }
                }
            });

            indicator.setFraction(1.0);
            indicator.setText("Scan completed");

            String summary = String.format("Java Files: %s, Classes: %s, Methods: %s",
                    javaFiles.size(), totalClasses.get(), totalMethods.get());
            log.info(summary);
            showNotification(project, summary, NotificationType.INFORMATION);

            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showInfoMessage(project, summary, "Scanner Worker - Scan Results"));

            ingestionIPC.sendChunks(chunks);

        } catch (Exception e) {
            log.error("Error during Java file scanning", e);
            ApplicationManager.getApplication().invokeLater(() -> {
                showNotification(project, "Error during scanning: " + e.getMessage(), NotificationType.ERROR);
                Messages.showErrorDialog(project, "Error during scanning: " + e.getMessage(), "Scanner Worker");
            });
        }
    }

    private void showNotification(Project project, String message, NotificationType type) {
        Notification notification = NOTIFICATION_GROUP.createNotification(
                SCANNER_WORKER,
                message,
                type
        );
        notification.notify(project);
    }
}
