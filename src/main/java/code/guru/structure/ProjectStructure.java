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
package code.guru.structure;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectStructure {

    private static final Logger log = Logger.getInstance(ProjectStructure.class);
    private final Project project;
    private final PsiManager psiManager;

    public ProjectStructure(Project project) {
        this.project = project;
        this.psiManager = PsiManager.getInstance(project);
    }

    public List<PsiJavaFile> getAllJavaPsiFiles() {
        List<PsiJavaFile> javaFiles = new ArrayList<>();

        try {
            // Get all Java files in the project scope
            Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(
                    JavaFileType.INSTANCE,
                    GlobalSearchScope.projectScope(project)
            );

            log.info("Found " + virtualFiles.size() + " Java virtual files");

            for (VirtualFile virtualFile : virtualFiles) {
                if (virtualFile.isValid()) {
                    PsiFile psiFile = psiManager.findFile(virtualFile);
                    if (psiFile instanceof PsiJavaFile) {
                        javaFiles.add((PsiJavaFile) psiFile);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error while scanning Java files", e);
        }

        return javaFiles;
    }
}
