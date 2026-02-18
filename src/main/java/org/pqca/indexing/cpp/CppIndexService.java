/*
 * CBOMkit-lib
 * Copyright (C) 2025 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pqca.indexing.cpp;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.pqca.indexing.IBuildType;
import org.pqca.indexing.IndexingService;
import org.pqca.progress.IProgressDispatcher;

public final class CppIndexService extends IndexingService {

    private static final List<String> CPP_FILE_EXTENSIONS =
            List.of(".cpp", ".cc", ".cxx", ".c", ".h", ".hpp");

    public CppIndexService(@Nonnull File baseDirectory) {
        this(null, baseDirectory);
    }

    public CppIndexService(
            @Nullable IProgressDispatcher progressDispatcher, @Nonnull File baseDirectory) {
        super(progressDispatcher, baseDirectory, "cxx", "");
        this.setExcludePatterns(null);
    }

    @Override
    public boolean isLanguageFile(@Nonnull File file) {
        return CPP_FILE_EXTENSIONS.stream().anyMatch(file.getName()::endsWith);
    }

    public void setExcludePatterns(@Nullable List<String> patterns) {
        if (patterns == null) {
            super.setExcludePatterns(
                    List.of(
                            "test/",
                            "tests/",
                            "third_party/",
                            "vendor/",
                            "build/",
                            "cmake-build-"));
        } else {
            super.setExcludePatterns(patterns);
        }
    }

    @Override
    public boolean isModule(@Nonnull File directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        for (String buildFileName : List.of("CMakeLists.txt", "Makefile", "GNUmakefile")) {
            final File file = new File(directory, buildFileName);
            if (file.exists() && file.isFile()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable public IBuildType getMainBuildTypeFromModuleDirectory(@Nonnull File directory) {
        if (!directory.isDirectory()) {
            return null;
        }
        final File cmakeFile = new File(directory, "CMakeLists.txt");
        if (cmakeFile.exists() && cmakeFile.isFile()) {
            return CppBuildType.CMAKE;
        }
        for (String makeFileName : List.of("Makefile", "GNUmakefile")) {
            final File makeFile = new File(directory, makeFileName);
            if (makeFile.exists() && makeFile.isFile()) {
                return CppBuildType.MAKE;
            }
        }
        return null;
    }
}
