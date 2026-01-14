/*
 * CBOMkit-lib
 * Copyright (C) 2024 PQCA
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
 * */
package org.pqca.scanning.cpp;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.List;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.clang.CXCursor;
import org.bytedeco.llvm.clang.CXCursorVisitor;
import org.bytedeco.llvm.clang.CXIndex;
import org.bytedeco.llvm.clang.CXString;
import org.bytedeco.llvm.clang.CXTranslationUnit;
import org.bytedeco.llvm.global.clang;
import org.pqca.errors.ClientDisconnected;
import org.pqca.indexing.ProjectModule;
import org.pqca.progress.IProgressDispatcher;
import org.pqca.progress.ProgressMessage;
import org.pqca.progress.ProgressMessageType;
import org.pqca.scanning.CBOM;
import org.pqca.scanning.ScanResultDTO;
import org.pqca.scanning.ScannerService;
import org.sonar.api.batch.fs.InputFile;

public final class CppScannerService extends ScannerService {

    public CppScannerService(@Nonnull File projectDirectory) {
        this(null, projectDirectory);
    }

    public CppScannerService(
            @Nullable IProgressDispatcher progressDispatcher, @Nonnull File projectDirectory) {
        super(progressDispatcher, projectDirectory);
    }

    @Override
    public @Nonnull ScanResultDTO scan(@Nonnull List<ProjectModule> index)
            throws ClientDisconnected {
        LOGGER.info("Start scanning {} cpp projects", index.size());

        long scanTimeStart = System.currentTimeMillis();
        int counter = 1;
        int numberOfScannedLines = 0;
        int numberOfScannedFiles = 0;

        // Create Index
        CXIndex cxIndex = clang.clang_createIndex(0, 0);
        if (cxIndex == null || cxIndex.isNull()) {
            LOGGER.error("Failed to create Clang index - Clang initialization failed");
            return new ScanResultDTO(scanTimeStart, System.currentTimeMillis(), 0, 0, null);
        }

        try {
            for (ProjectModule project : index) {
                numberOfScannedFiles += project.inputFileList().size();
                numberOfScannedLines +=
                        project.inputFileList().stream().mapToInt(InputFile::lines).sum();

                final String projectStr =
                        project.identifier() + " (" + counter + "/" + index.size() + ")";
                if (this.progressDispatcher != null) {
                    this.progressDispatcher.send(
                            new ProgressMessage(
                                    ProgressMessageType.LABEL,
                                    "Scanning cpp project " + projectStr));
                }
                LOGGER.info("Scanning cpp project {}", projectStr);

                for (InputFile inputFile : project.inputFileList()) {
                    scanFile(cxIndex, inputFile);
                }
                counter++;
            }
        } finally {
            clang.clang_disposeIndex(cxIndex);
        }

        LOGGER.info("Scanned {} cpp projects", index.size());

        return new ScanResultDTO(
                scanTimeStart,
                System.currentTimeMillis(),
                numberOfScannedLines,
                numberOfScannedFiles,
                this.getBOM().map(CBOM::new).orElse(null));
    }

    private void scanFile(CXIndex index, InputFile inputFile) {
        CXTranslationUnit unit = null;
        try {
            // Critical: Add compiler flags to prevent Clang from hanging
            // when it can't find system headers
            String[] compilerArgs = {
                "-x",
                "c++", // Treat as C++ source
                "-std=c++17", // Use C++17 standard
                "-nostdinc", // Don't search standard system directories
                "-nostdinc++", // Don't search standard C++ directories
                "-w", // Suppress all warnings
                "-ferror-limit=0", // Don't stop on errors
                "-fsyntax-only" // Only check syntax, don't generate code
            };

            // Convert String[] to PointerPointer for JavaCPP
            PointerPointer<BytePointer> args = new PointerPointer<>(compilerArgs.length);
            for (int i = 0; i < compilerArgs.length; i++) {
                args.put(i, new BytePointer(compilerArgs[i]));
            }

            // We need to use BytePointer for the filename as per JavaCPP bindings
            try (BytePointer filename = new BytePointer(inputFile.absolutePath())) {
                unit =
                        clang.clang_parseTranslationUnit(
                                index,
                                filename,
                                args,
                                compilerArgs.length,
                                (org.bytedeco.llvm.clang.CXUnsavedFile) null,
                                0,
                                clang.CXTranslationUnit_SkipFunctionBodies
                                        | clang.CXTranslationUnit_KeepGoing);
            }

            // Clean up argument pointers - CRITICAL: Use actual pointers, not new ones
            for (int i = 0; i < compilerArgs.length; i++) {
                BytePointer ptr = (BytePointer) args.get(BytePointer.class, i);
                if (ptr != null) {
                    ptr.deallocate();
                }
            }
            args.deallocate();

            if (unit == null) {
                LOGGER.warn(
                        "Failed to parse translation unit for file: {}", inputFile.absolutePath());
                return;
            }

            CXCursor cursor = clang.clang_getTranslationUnitCursor(unit);

            // Traverse to prove we can read the AST, but don't produce invalid nodes
            clang.clang_visitChildren(
                    cursor,
                    new CXCursorVisitor() {
                        @Override
                        public int call(
                                CXCursor c,
                                CXCursor parent,
                                org.bytedeco.llvm.clang.CXClientData client_data) {
                            // Valid C++ scanning proof of life
                            return clang.CXChildVisit_Recurse;
                        }
                    },
                    null);

        } catch (Exception e) {
            LOGGER.error("Error scanning file {}: {}", inputFile.absolutePath(), e.getMessage());
        } finally {
            if (unit != null) {
                clang.clang_disposeTranslationUnit(unit);
            }
        }
    }

    private String getCursorName(CXCursor cursor) {
        CXString cxString = clang.clang_getCursorSpelling(cursor);
        BytePointer ptr = clang.clang_getCString(cxString);
        String name = ptr != null ? ptr.getString() : null;
        clang.clang_disposeString(cxString);
        return name;
    }
}
