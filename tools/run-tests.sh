#!/usr/bin/env bash
#
# Compiles and runs the Android-free unit tests on the host, with **exactly**
# the classpath CI gives them — nothing more.
#
# Why the "nothing more" matters: StringsTest uses kotlin.reflect, which was on
# the sandbox classpath by accident and is not a project dependency. The suite
# passed here and failed to compile in CI. A harness that is more generous than
# the real build tells you nothing, so this one reads its dependencies from
# app/build.gradle.kts and refuses to add any of its own.
#
# Tests that touch Android (or org.json) are skipped: they need the SDK, and CI
# runs the full suite anyway. This is the fast loop, not a replacement.
#
# Usage:  tools/run-tests.sh
# Needs:  a JDK, and Gradle's bundled kotlin compiler + junit jars.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_LIB=/opt/gradle-8.14.3/lib
if ! ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar >/dev/null 2>&1; then
    echo "No kotlin-compiler-embeddable jar under $GRADLE_LIB — skipping." >&2
    exit 0
fi

# --- the test classpath, derived from the build file, not invented here ------
DEPS=("$GRADLE_LIB/kotlin-stdlib-2.0.21.jar")
add_if_declared() {  # $1 = gradle coordinate fragment, $2 = jar
    if grep -q "testImplementation(\"$1" "$REPO_ROOT/app/build.gradle.kts"; then
        [ -f "$2" ] && DEPS+=("$2")
    fi
}
add_if_declared "junit:junit"                       "$GRADLE_LIB/junit-4.13.2.jar"
add_if_declared "junit:junit"                       "$GRADLE_LIB/hamcrest-core-1.3.jar"
add_if_declared "org.jetbrains.kotlin:kotlin-reflect" "$GRADLE_LIB/kotlin-reflect-2.0.21.jar"

TEST_CP="$(IFS=:; echo "${DEPS[*]}")"
COMPILER_CP="$(ls "$GRADLE_LIB"/kotlin-*.jar "$GRADLE_LIB"/kotlinx-*.jar \
                  "$GRADLE_LIB"/annotations-*.jar "$GRADLE_LIB"/trove4j*.jar 2>/dev/null | tr '\n' ':')"

# --- sources: everything that doesn't need the Android SDK -------------------
SOURCES=(
    app/src/main/java/com/basel/ai/core/Strings.kt
    app/src/main/java/com/basel/ai/core/DeviceState.kt
    app/src/main/java/com/basel/ai/chat/Markdown.kt
    app/src/main/java/com/basel/ai/chat/ChatMessage.kt
    app/src/main/java/com/basel/ai/chat/Conversation.kt
    app/src/main/java/com/basel/ai/core/AutoPolicy.kt
    app/src/main/java/com/basel/ai/core/TurnMetrics.kt
    app/src/main/java/com/basel/ai/llm/QueryRouter.kt
    app/src/main/java/com/basel/ai/llm/BackendPlanner.kt
    app/src/main/java/com/basel/ai/llm/ThinkingStreamParser.kt
    app/src/main/java/com/basel/ai/web/SearchModels.kt
    app/src/main/java/com/basel/ai/web/HtmlExtract.kt
    app/src/main/java/com/basel/ai/model/ModelAdvisor.kt
    app/src/main/java/com/basel/ai/studio/CodeExtractor.kt
    app/src/main/java/com/basel/ai/studio/StudioPrompt.kt
    app/src/main/java/com/basel/ai/ui/theme/Layout.kt
    app/src/main/java/com/basel/ai/audio/VoicePicker.kt
    app/src/main/java/com/basel/ai/audio/CloudTts.kt
    app/src/main/java/com/basel/ai/audio/SpeechText.kt
    app/src/main/java/com/basel/ai/agent/MiniJson.kt
    app/src/main/java/com/basel/ai/agent/AgentTool.kt
    app/src/main/java/com/basel/ai/agent/ToolCallParser.kt
    app/src/main/java/com/basel/ai/agent/ToolPrompt.kt
    app/src/main/java/com/basel/ai/agent/CommandPolicy.kt
    app/src/main/java/com/basel/ai/agent/Calc.kt
    app/src/main/java/com/basel/ai/agent/SandboxPaths.kt
    app/src/main/java/com/basel/ai/pdf/PdfModels.kt
    app/src/main/java/com/basel/ai/pdf/TextLayerQuality.kt
    app/src/main/java/com/basel/ai/pdf/DocumentIndex.kt
    app/src/main/java/com/basel/ai/agent/PdfTools.kt
    app/src/main/java/com/basel/ai/ocr/CloudOcr.kt
    app/src/test/java/com/basel/ai/core/StringsTest.kt
    app/src/test/java/com/basel/ai/core/AutoPolicyTest.kt
    app/src/test/java/com/basel/ai/core/TurnMetricsTest.kt
    app/src/test/java/com/basel/ai/chat/MarkdownTest.kt
    app/src/test/java/com/basel/ai/chat/ConversationIndexTest.kt
    app/src/test/java/com/basel/ai/llm/QueryRouterTest.kt
    app/src/test/java/com/basel/ai/llm/BackendPlannerTest.kt
    app/src/test/java/com/basel/ai/llm/ThinkingStreamParserTest.kt
    app/src/test/java/com/basel/ai/web/SearchQueryTest.kt
    app/src/test/java/com/basel/ai/web/HtmlExtractTest.kt
    app/src/test/java/com/basel/ai/model/ModelAdvisorTest.kt
    app/src/test/java/com/basel/ai/studio/CodeExtractorTest.kt
    app/src/test/java/com/basel/ai/studio/StudioPromptTest.kt
    app/src/test/java/com/basel/ai/ui/theme/LayoutTest.kt
    app/src/test/java/com/basel/ai/audio/VoicePickerTest.kt
    app/src/test/java/com/basel/ai/audio/CloudTtsTest.kt
    app/src/test/java/com/basel/ai/audio/SpeechTextTest.kt
    app/src/test/java/com/basel/ai/agent/MiniJsonTest.kt
    app/src/test/java/com/basel/ai/agent/ToolCallParserTest.kt
    app/src/test/java/com/basel/ai/agent/ToolPromptTest.kt
    app/src/test/java/com/basel/ai/agent/CommandPolicyTest.kt
    app/src/test/java/com/basel/ai/agent/CalcTest.kt
    app/src/test/java/com/basel/ai/agent/SandboxPathsTest.kt
    app/src/test/java/com/basel/ai/pdf/TextLayerQualityTest.kt
    app/src/test/java/com/basel/ai/pdf/DocumentIndexTest.kt
    app/src/test/java/com/basel/ai/agent/PageRangeTest.kt
    app/src/test/java/com/basel/ai/ocr/CloudOcrTest.kt
)
CLASSES=(
    com.basel.ai.core.StringsTest
    com.basel.ai.core.AutoPolicyTest
    com.basel.ai.core.TurnMetricsTest
    com.basel.ai.chat.MarkdownTest
    com.basel.ai.chat.ConversationIndexTest
    com.basel.ai.llm.QueryRouterTest
    com.basel.ai.llm.BackendPlannerTest
    com.basel.ai.llm.ThinkingStreamParserTest
    com.basel.ai.web.SearchQueryTest
    com.basel.ai.web.HtmlExtractTest
    com.basel.ai.model.ModelAdvisorTest
    com.basel.ai.studio.CodeExtractorTest
    com.basel.ai.studio.StudioPromptTest
    com.basel.ai.ui.theme.LayoutTest
    com.basel.ai.audio.VoicePickerTest
    com.basel.ai.audio.CloudTtsTest
    com.basel.ai.audio.SpeechTextTest
    com.basel.ai.agent.MiniJsonTest
    com.basel.ai.agent.ToolCallParserTest
    com.basel.ai.agent.ToolPromptTest
    com.basel.ai.agent.CommandPolicyTest
    com.basel.ai.agent.CalcTest
    com.basel.ai.agent.SandboxPathsTest
    com.basel.ai.pdf.TextLayerQualityTest
    com.basel.ai.pdf.DocumentIndexTest
    com.basel.ai.agent.PageRangeTest
    com.basel.ai.ocr.CloudOcrTest
)

# BackendPlanner references BackendPref, which lives in a file that imports
# org.json. A minimal stand-in keeps the SDK out of this loop.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cat > "$WORK/BackendPref.kt" <<'EOF'
package com.basel.ai.model
enum class BackendPref { AUTO, CPU, GPU, NPU }
EOF

cd "$REPO_ROOT"
echo "==> compiling with the CI test classpath"
if ! java -Dfile.encoding=UTF-8 -cp "$COMPILER_CP" \
        org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        "$WORK/BackendPref.kt" "${SOURCES[@]}" \
        -classpath "$TEST_CP" -d "$WORK/out" -nowarn 2>&1 \
        | grep -vE "^warning:|^Picked up" | tee "$WORK/compile.log"
then :; fi
if grep -q "error:" "$WORK/compile.log"; then
    echo ">>> COMPILE FAILED"
    exit 1
fi

echo "==> running"
java -Dfile.encoding=UTF-8 -cp "$WORK/out:$TEST_CP" \
    org.junit.runner.JUnitCore "${CLASSES[@]}" 2>&1 | grep -v "^Picked up"
