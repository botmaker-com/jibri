/*
 * Copyright @ 2021 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.jibri.service.impl

import org.jitsi.jibri.botmaker.JibriPal
import org.jitsi.jibri.service.JibriServiceFinalizer

class JibriServiceFinalizeCommandRunner(
//    private val processFactory: ProcessFactory = ProcessFactory(),
    private val sessionRecordingDirectory: String
) : JibriServiceFinalizer {

//    private val logger = createLogger()

    /**
     * Helper to execute the finalize script and wait for its completion.
     * NOTE that this will block for however long the finalize script takes
     * to complete (by design)
     */
    override fun doFinalize() {
//        try {
        JibriPal().stopService(sessionRecordingDirectory)

//            with(processFactory.createProcess(finalizeCommand, logger)) {
//                start()
//                val streamDone = LoggingUtils.logOutputOfProcess(this, logger)
//                waitFor()
//                // Make sure we get all the logs
//                try {
//                    streamDone.get(10, TimeUnit.SECONDS)
//                } catch (e: TimeoutException) {
//                    logger.error("Timed out waiting for process logger task to complete")
//                    streamDone.cancel(true)
//                } catch (e: Exception) {
//                    logger.error("Exception while waiting for process logger task to complete", e)
//                    streamDone.cancel(true)
//                }
//                logger.info("Finalize script finished with exit value $exitValue")
//            }
//        } catch (e: Exception) {
//            logger.error("Failed to run finalize script", e)
//        }
    }
}
