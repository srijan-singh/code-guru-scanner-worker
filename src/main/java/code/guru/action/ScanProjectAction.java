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

import code.guru.structure.ProjectStructure;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import java.util.List;

public class ScanProjectAction extends AnAction {

    private static final Logger log = Logger.getInstance(ScanProjectAction.class);

    // Create a notification group for our plugin
    private static final NotificationGroup NOTIFICATION_GROUP =
            NotificationGroup.balloonGroup("Scanner Worker");

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            log.warn("No project found in context.");
            Messages.showErrorDialog("No project found in context.", "Scanner Worker");
            return;
        }

        log.info("Starting Java file scan for project: " + project.getName());

        try {
            ProjectStructure structure = new ProjectStructure(project);
            List<PsiJavaFile> javaFiles = structure.getAllJavaPsiFiles();

            log.info("Found " + javaFiles.size() + " Java files in project " + project.getName());

            // Show a notification to the user
            showNotification(project, "Found " + javaFiles.size() + " Java files", NotificationType.INFORMATION);

            int totalClasses = 0;
            int totalMethods = 0;

            for (PsiJavaFile file : javaFiles) {
                if (file.getVirtualFile() != null) {
                    log.info("File: " + file.getVirtualFile().getPath());
                } else {
                    log.info("File: " + file.getName() + " (no virtual file)");
                }

                PsiClass[] classes = file.getClasses();
                if (classes.length == 0) {
                    log.info("  No classes found in this file.");
                } else {
                    totalClasses += classes.length;

                    for (PsiClass psiClass : classes) {
                        String className = psiClass.getName();
                        String qualifiedName = psiClass.getQualifiedName();

                        log.info("  Class: " + className + " (Qualified Name: " + qualifiedName + ")");

                        PsiMethod[] methods = psiClass.getMethods();
                        if (methods.length == 0) {
                            log.info("    No methods found.");
                        } else {
                            totalMethods += methods.length;

                            for (PsiMethod method : methods) {
                                String methodName = method.getName();
                                String parameters = method.getParameterList().getText();
                                log.info("    Method: " + methodName + parameters);
                            }
                        }
                    }
                }
            }

            // Show summary
            String summary = String.format("Scan complete: %d files, %d classes, %d methods",
                    javaFiles.size(), totalClasses, totalMethods);
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

    private void showNotification(Project project, String message, NotificationType type) {
        Notification notification = NOTIFICATION_GROUP.createNotification(
                "Scanner Worker",
                message,
                type
        );
        notification.notify(project);
    }
}