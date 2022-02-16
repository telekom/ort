package org.ossreviewtoolkit.oscake.common

import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

data class ActionInfo (
        val loggerName: String,
        val phase: ProcessingPhase,
        val author: String,
        val release: String,
        val actor: String,
        val suffix: String,
        val issueLevel: Int
)

{
}
