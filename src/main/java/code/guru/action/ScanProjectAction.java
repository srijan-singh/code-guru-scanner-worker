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
import code.guru.ipc.JsonStorageImplementation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ScanProjectAction extends AnAction {

    private static final String SCANNER_WORKER = "Scanner Worker";

    private static final Logger log = Logger.getInstance(ScanProjectAction.class);

    // Create a notification group for our plugin
    private static final NotificationGroup NOTIFICATION_GROUP =
            NotificationGroupManager.getInstance().getNotificationGroup(SCANNER_WORKER);

    private IngestionIPC ingestionIPC;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            log.warn("No project found in context.");
            Messages.showErrorDialog("No project found in context.", SCANNER_WORKER);
            return;
        }

        ingestionIPC = new JsonStorageImplementation(project);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning Java Files", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
                performChunkScanning(project, indicator);
                }
            });
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

            String info = "Found %s Java files in project %s".formatted(javaFiles.size(), project.getName());
            log.info(info);
            showNotification(project, info, NotificationType.INFORMATION);

            indicator.setIndeterminate(false);
            indicator.setFraction(0.0);

            // Map to store method signature -> list of callers
            Map<String, List<String>> calledByMap = new HashMap<>();
            
            // PASS 1: Build dependency graph and collect chunks
            ApplicationManager.getApplication().runReadAction(() -> {
                for (int i = 0; i < javaFiles.size(); i++) {
                    if (indicator.isCanceled()) break;

                    PsiJavaFile file = javaFiles.get(i);
                    double progress = (double) i / javaFiles.size() * 0.5; // First pass is 50%
                    indicator.setFraction(progress);
                    indicator.setText("Pass 1/2: Analyzing dependencies (" + (i+1) + "/" + javaFiles.size() + ")");

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

                                    // Create unique method signature for this method
                                    String currentMethodSignature = className + "." + methodName;

                                    List<String> dependencies = new ArrayList<>();

                                    method.accept(new JavaRecursiveElementVisitor() {
                                        @Override
                                        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                                            PsiMethod resolvedMethod = expression.resolveMethod();
                                            if (resolvedMethod != null) {
                                                PsiClass containingClass = resolvedMethod.getContainingClass();
                                                if (containingClass != null) {
                                                    String fqMethod = containingClass.getQualifiedName() + "." + resolvedMethod.getName();
                                                    dependencies.add(fqMethod);
                                                    
                                                    // Build reverse mapping: the called method is called by current method
                                                    calledByMap.computeIfAbsent(fqMethod, k -> new ArrayList<>())
                                                            .add(currentMethodSignature);
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

                                    // Create chunk with empty calledBy for now
                                    ChunkData chunk = ChunkData.builder()
                                            .className(className)
                                            .methodName(methodName)
                                            .returnType(returnType)
                                            .parameters(parameters)
                                            .classAttributes(classAttributes)
                                            .calledBy(new ArrayList<>()) // Will populate in pass 2
                                            .dependencies(dependencies)
                                            .methodCode(methodCode)
                                            .build();

                                    chunks.add(chunk);
                                    
                                    if (chunks.size() % 100 == 0) {
                                        log.info("Collected " + chunks.size() + " chunks so far");
                                    }
                                }
                            }
                        }
                    }
                }
            });

            if (indicator.isCanceled()) {
                log.info("Scan cancelled by user");
                return;
            }

            // PASS 2: Populate calledBy information
            indicator.setText("Pass 2/2: Building caller relationships");
            for (int i = 0; i < chunks.size(); i++) {
                if (indicator.isCanceled()) break;
                
                double progress = 0.5 + (double) i / chunks.size() * 0.5; // Second pass is remaining 50%
                indicator.setFraction(progress);
                
                ChunkData chunk = chunks.get(i);
                String methodSignature = chunk.getClassName() + "." + chunk.getMethodName();
                
                // Get callers from the map
                List<String> callers = calledByMap.getOrDefault(methodSignature, new ArrayList<>());
                
                // Update the chunk with calledBy information
                // Note: You'll need to modify ChunkData to allow updating calledBy
                // or rebuild the chunk with the new information
                chunk.getCalledBy().addAll(callers);
                
                if ((i + 1) % 100 == 0) {
                    log.info("Processed calledBy for " + (i + 1) + "/" + chunks.size() + " chunks");
                }
            }

            indicator.setFraction(1.0);
            indicator.setText("Scan completed");

            // Log some statistics
            long chunksWithCallers = chunks.stream()
                    .filter(chunk -> !chunk.getCalledBy().isEmpty())
                    .count();
            log.info("Methods with callers: " + chunksWithCallers + " out of " + chunks.size());

            String chunkIngestionResult = ingestionIPC.sendChunks(chunks);

            String summary = String.format("Java Files: %s, Classes: %s, Methods: %s, Methods with callers: %s",
                    javaFiles.size(), totalClasses.get(), totalMethods.get(), chunksWithCallers);
            log.info(summary);
            showNotification(project, summary, NotificationType.INFORMATION);

            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showInfoMessage(project, summary, chunkIngestionResult));

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
