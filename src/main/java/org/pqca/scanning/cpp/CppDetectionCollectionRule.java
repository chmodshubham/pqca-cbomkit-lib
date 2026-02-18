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
package org.pqca.scanning.cpp;

import com.ibm.engine.detection.Finding;
import com.ibm.mapper.model.INode;
import com.ibm.plugin.rules.CxxInventoryRule;
import com.sonar.cxx.sslr.api.AstNode;
import com.sonar.cxx.sslr.api.Grammar;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;
import org.sonar.cxx.squidbridge.SquidAstVisitorContext;
import org.sonar.cxx.squidbridge.api.Symbol;
import org.sonar.cxx.squidbridge.checks.SquidCheck;

public class CppDetectionCollectionRule extends CxxInventoryRule {
    private final Consumer<List<INode>> handler;

    public CppDetectionCollectionRule(@Nonnull Consumer<List<INode>> findingConsumer) {
        this.handler = findingConsumer;
    }

    @Override
    public void update(
            @Nonnull
                    Finding<
                                    SquidCheck<?>,
                                    AstNode,
                                    Symbol,
                                    SquidAstVisitorContext<? extends Grammar>>
                            finding) {
        super.update(finding);
        final List<INode> nodes = cxxTranslationProcess.initiate(finding.detectionStore());
        handler.accept(nodes);
    }
}
