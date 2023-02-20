/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.common.flicker.extractors

import android.tools.common.CrossPlatform
import android.tools.common.flicker.ScenarioInstance
import android.tools.common.flicker.config.FaasScenarioType
import android.tools.common.io.IReader
import android.tools.common.traces.events.CujType
import kotlin.math.max

class TaggedScenarioExtractor(
    val targetTag: CujType,
    val type: FaasScenarioType,
    val transitionMatcher: ITransitionMatcher
) : IScenarioExtractor {
    override fun extract(reader: IReader): List<ScenarioInstance> {

        val wmTrace = reader.readWmTrace()
        val layersTrace = reader.readLayersTrace() ?: error("Missing layers trace")
        val cujTrace = reader.readCujTrace() ?: error("Missing CUJ trace")

        val targetCujEntries =
            cujTrace.entries.filter { it.cuj === targetTag }.filter { !it.canceled }

        if (targetCujEntries.isEmpty()) {
            // No scenarios to extract here
            return emptyList()
        }

        return targetCujEntries.map { cujEntry ->
            val associatedTransition = transitionMatcher.getTransition(cujEntry, reader)

            require(
                cujEntry.startTimestamp.hasAllTimestamps && cujEntry.endTimestamp.hasAllTimestamps
            )

            // There is a delay between when we flag that transition as finished with the CUJ tags
            // and when it is actually finished on the SF side. We try and account for that by
            // checking when the finish transaction is actually applied.
            // TODO: Figure out how to get the vSyncId that the Jank tracker actually gets to avoid
            //       relying on the transition and have a common end point.
            val finishTransactionAppliedTimestamp =
                if (associatedTransition != null) {
                    val transactionsTrace =
                        reader.readTransactionsTrace() ?: error("Missing transactions trace")
                    val finishTransaction =
                        associatedTransition.getFinishTransaction(transactionsTrace)
                            ?: error("Couldn't find finish transaction")
                    layersTrace.getEntryForTransaction(finishTransaction).timestamp
                } else {
                    CrossPlatform.timestamp.min()
                }

            val wmEntryAtTransitionFinished =
                wmTrace?.entries?.first { it.timestamp >= finishTransactionAppliedTimestamp }

            val startTimestamp = cujEntry.startTimestamp
            val endTimestamp =
                CrossPlatform.timestamp.from(
                    elapsedNanos =
                        max(
                            cujEntry.endTimestamp.elapsedNanos,
                            wmEntryAtTransitionFinished?.timestamp?.elapsedNanos ?: -1L
                        ),
                    systemUptimeNanos =
                        max(
                            cujEntry.endTimestamp.systemUptimeNanos,
                            finishTransactionAppliedTimestamp.systemUptimeNanos
                        ),
                    unixNanos =
                        max(
                            cujEntry.endTimestamp.unixNanos,
                            finishTransactionAppliedTimestamp.unixNanos
                        )
                )

            ScenarioInstance(
                type,
                startRotation =
                    layersTrace
                        .getEntryAt(startTimestamp)
                        .displays
                        .first { !it.isVirtual && it.layerStackSpace.isNotEmpty }
                        .transform
                        .getRotation(),
                endRotation =
                    layersTrace
                        .getEntryAt(endTimestamp)
                        .displays
                        .first { !it.isVirtual && it.layerStackSpace.isNotEmpty }
                        .transform
                        .getRotation(),
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                associatedCuj = cujEntry.cuj,
                associatedTransition = associatedTransition,
                reader = reader.slice(startTimestamp, endTimestamp)
            )
        }
    }
}
