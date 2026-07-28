package com.adhar.kit.profiler.jfr;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the {@code jdk.ExecutionSample} events of a JFR dump into collapsed-stack format:
 * one line per unique stack, with semicolon-joined frames (root frame first) followed by a
 * space and the number of samples observed for that stack. The output can be fed directly to
 * standard flame graph tooling (e.g. Brendan Gregg's {@code flamegraph.pl} or speedscope).
 */
public class FlameGraphExporter {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    /**
     * Parses the given JFR dump and returns its CPU execution samples in collapsed-stack format.
     *
     * @param jfrFile path to a JFR dump file
     * @return collapsed-stack text, empty when the dump contains no execution samples
     * @throws IOException if the file cannot be read or is not a valid JFR recording
     */
    public String exportCollapsed(Path jfrFile) throws IOException {
        Map<String, Long> sampleCounts = new LinkedHashMap<>();
        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (!EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    continue;
                }
                RecordedStackTrace stackTrace = event.getStackTrace();
                if (stackTrace == null) {
                    continue;
                }
                String collapsedStack = toCollapsedStack(stackTrace.getFrames());
                if (!collapsedStack.isEmpty()) {
                    sampleCounts.merge(collapsedStack, 1L, Long::sum);
                }
            }
        }
        return render(sampleCounts);
    }

    /**
     * Joins the Java frames of a single stack into {@code root;caller;...;leaf} form.
     * JFR reports frames leaf-first, so they are reversed here to the root-first order the
     * collapsed format expects.
     */
    private String toCollapsedStack(List<RecordedFrame> frames) {
        StringBuilder stack = new StringBuilder();
        for (int i = frames.size() - 1; i >= 0; i--) {
            RecordedFrame frame = frames.get(i);
            if (!frame.isJavaFrame()) {
                continue;
            }
            RecordedMethod method = frame.getMethod();
            if (!stack.isEmpty()) {
                stack.append(';');
            }
            stack.append(method.getType().getName()).append('.').append(method.getName());
        }
        return stack.toString();
    }

    /**
     * Renders collapsed stacks and their sample counts as one {@code stack count} line each.
     */
    static String render(Map<String, Long> sampleCounts) {
        StringBuilder output = new StringBuilder();
        sampleCounts.forEach((stack, count) ->
                output.append(stack).append(' ').append(count).append('\n'));
        return output.toString();
    }
}
