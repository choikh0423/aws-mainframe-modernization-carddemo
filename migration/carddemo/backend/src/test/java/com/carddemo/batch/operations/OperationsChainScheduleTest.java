package com.carddemo.batch.operations;

import com.carddemo.batch.operations.OperationsChainSchedule.JobTrigger;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-14: the orchestration contract must stay identical to the scheduler
 * definitions it documents. This test re-derives the dependency graph from
 * {@code app/scheduler/CardDemo.controlm} and {@code app/scheduler/CardDemo.ca7}
 * and compares it with {@link OperationsChainSchedule} and with the markdown
 * contract the other batch streams read.
 *
 * <p>Covers FR-OC-30, FR-OC-31, FR-OC-32.
 */
class OperationsChainScheduleTest {

    private static final Path REPO_ROOT = Path.of("..", "..", "..");
    private static final Path CONTROL_M = REPO_ROOT.resolve("app/scheduler/CardDemo.controlm");
    private static final Path CA7 = REPO_ROOT.resolve("app/scheduler/CardDemo.ca7");
    private static final Path CONTRACT = REPO_ROOT.resolve(
            "docs/migration/streams/OperationsChain/OperationsChain_orchestration_contract.md");

    @Test
    void controlMGraphMatchesTheFolderDefinitions() throws Exception {
        List<JobTrigger> parsed = parseControlM();

        assertThat(parsed)
                .containsExactlyInAnyOrderElementsOf(OperationsChainSchedule.controlM());
    }

    @Test
    void ca7GraphMatchesTheLjobListing() throws Exception {
        List<JobTrigger> parsed = parseCa7();

        assertThat(parsed)
                .extracting(JobTrigger::predecessor, JobTrigger::successor, JobTrigger::condition)
                .containsExactlyElementsOf(OperationsChainSchedule.ca7().stream()
                        .map(t -> org.assertj.core.groups.Tuple.tuple(
                                t.predecessor(), t.successor(), t.condition()))
                        .toList());
    }

    @Test
    void contractDocumentListsEveryTrigger() throws Exception {
        String document = Files.readString(CONTRACT, StandardCharsets.UTF_8);

        List<JobTrigger> all = new ArrayList<>(OperationsChainSchedule.controlM());
        all.addAll(OperationsChainSchedule.ca7());
        for (JobTrigger trigger : all) {
            String row = "| " + trigger.predecessor() + " | " + trigger.successor()
                    + " | " + trigger.condition() + " |";
            assertThat(document)
                    .as("row for %s -> %s (%s)", trigger.predecessor(), trigger.successor(),
                            trigger.condition())
                    .contains(row);
        }
    }

    /** Control-M releases a job when the condition another job posted with SIGN="+" exists. */
    private List<JobTrigger> parseControlM() throws Exception {
        Element root = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File(CONTROL_M.toString()))
                .getDocumentElement();

        Map<String, String> producerOfCondition = new LinkedHashMap<>();
        for (Element job : elements(root.getElementsByTagName("JOB"))) {
            for (Element out : childElements(job, "OUTCOND")) {
                if ("+".equals(out.getAttribute("SIGN"))) {
                    producerOfCondition.put(out.getAttribute("NAME"), job.getAttribute("JOBNAME"));
                }
            }
        }

        List<JobTrigger> triggers = new ArrayList<>();
        for (Element job : elements(root.getElementsByTagName("JOB"))) {
            for (Element in : childElements(job, "INCOND")) {
                String condition = in.getAttribute("NAME");
                triggers.add(new JobTrigger(folderOf(job), producerOfCondition.get(condition),
                        job.getAttribute("JOBNAME"), condition));
            }
        }
        return triggers;
    }

    /**
     * CA-7 prints one LJOB block per job; the TRIGGERED JOBS section of a block
     * lists the jobs its completion submits, with the schedule id.
     */
    private List<JobTrigger> parseCa7() throws Exception {
        Pattern jobBlock = Pattern.compile("^\\s*1LJOB,JOB=(\\w+),LIST");
        Pattern triggered = Pattern.compile("^\\s+JOB=(\\w+)\\s+SCHID=(\\d+)");

        List<JobTrigger> triggers = new ArrayList<>();
        String current = null;
        for (String line : Files.readAllLines(CA7, StandardCharsets.UTF_8)) {
            Matcher block = jobBlock.matcher(line);
            if (block.find()) {
                current = block.group(1);
                continue;
            }
            Matcher trigger = triggered.matcher(line);
            if (current != null && trigger.find()) {
                triggers.add(new JobTrigger("", current, trigger.group(1), trigger.group(2)));
            }
        }
        return triggers;
    }

    /** The folder a job is defined in - FOLDER or SMART_FOLDER, whichever encloses it. */
    private String folderOf(Element job) {
        Node parent = job.getParentNode();
        return ((Element) parent).getAttribute("FOLDER_NAME");
    }

    private List<Element> elements(NodeList nodes) {
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    private List<Element> childElements(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && tag.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }
}
