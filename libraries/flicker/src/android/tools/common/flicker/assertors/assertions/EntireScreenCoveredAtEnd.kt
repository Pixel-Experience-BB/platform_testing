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

package android.tools.common.flicker.assertors.assertions

import android.tools.common.flicker.IScenarioInstance
import android.tools.common.flicker.assertors.AssertionTemplate
import android.tools.common.flicker.subject.layers.LayersTraceSubject

/**
 * Checks if the stack space of all displays is fully covered by any visible layer, at the end of
 * the transition
 */
class EntireScreenCoveredAtEnd : AssertionTemplate() {
    /** {@inheritDoc} */
    override fun doEvaluate(scenarioInstance: IScenarioInstance, layerSubject: LayersTraceSubject) {
        val subject = layerSubject.last()
        val displays = subject.entry.displays
        if (displays.isEmpty()) {
            subject.fail("No displays found")
        }
        displays.forEach { display ->
            subject.visibleRegion().coversAtLeast(display.layerStackSpace)
        }
    }
}