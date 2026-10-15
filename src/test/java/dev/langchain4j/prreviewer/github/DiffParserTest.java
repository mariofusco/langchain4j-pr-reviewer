package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.ChangeKind;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffParserTest {

    @Test
    void parses_a_real_pull_request_diff() {
        List<FileChange> files = DiffParser.parse(FixtureGitHubGateway.diff6610());

        assertThat(files).hasSize(6);
        FileChange validation = files.get(0);
        assertThat(validation.path()).isEqualTo("langchain4j/src/main/java/dev/langchain4j/service/AiServiceValidation.java");
        assertThat(validation.kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(validation.hunks()).hasSize(2);
        assertThat(validation.hunks().get(0).newStart()).isEqualTo(57);
        assertThat(validation.hunks().get(0).newCount()).isEqualTo(22);
        // every hunk holds exactly the lines it declares
        for (FileChange file : files) {
            file.hunks().forEach(h -> {
                long newSide = h.lines().stream().filter(l -> !l.startsWith("-")).count();
                long oldSide = h.lines().stream().filter(l -> !l.startsWith("+")).count();
                assertThat(newSide).isEqualTo(h.newCount());
                assertThat(oldSide).isEqualTo(h.oldCount());
            });
        }
        int additions = files.stream().mapToInt(FileChange::additions).sum();
        int deletions = files.stream().mapToInt(FileChange::deletions).sum();
        assertThat(additions).isEqualTo(124);
        assertThat(deletions).isEqualTo(75);
    }

    @Test
    void recognizes_added_deleted_renamed_and_binary_files() {
        String diff = """
                diff --git a/src/New.java b/src/New.java
                new file mode 100644
                --- /dev/null
                +++ b/src/New.java
                @@ -0,0 +1,2 @@
                +class New {
                +}
                diff --git a/src/Old.java b/src/Old.java
                deleted file mode 100644
                --- a/src/Old.java
                +++ /dev/null
                @@ -1 +0,0 @@
                -class Old {}
                diff --git a/src/A.java b/src/B.java
                similarity index 90%
                rename from src/A.java
                rename to src/B.java
                --- a/src/A.java
                +++ b/src/B.java
                @@ -1,2 +1,2 @@
                -class A {}
                +class B {}
                 // end
                \\ No newline at end of file
                diff --git a/logo.png b/logo.png
                Binary files a/logo.png and b/logo.png differ
                """;

        List<FileChange> files = DiffParser.parse(diff);

        assertThat(files).extracting(FileChange::path).containsExactly("src/New.java", "src/Old.java", "src/B.java", "logo.png");
        assertThat(files).extracting(FileChange::kind)
                .containsExactly(ChangeKind.ADDED, ChangeKind.DELETED, ChangeKind.RENAMED, ChangeKind.MODIFIED);
        assertThat(files.get(2).oldPath()).isEqualTo("src/A.java");
        assertThat(files.get(2).hunks().get(0).lines()).hasSize(3);
        assertThat(files.get(3).binary()).isTrue();
        assertThat(files.get(1).covers(1, 0)).isTrue(); // deleted files are located on their old lines
    }

    @Test
    void lines_starting_like_headers_inside_hunks_are_content() {
        String diff = """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1,2 +1,2 @@
                ---- old
                ++++ new
                 kept
                """;

        FileChange file = DiffParser.parse(diff).get(0);

        assertThat(file.additions()).isEqualTo(1);
        assertThat(file.deletions()).isEqualTo(1);
        assertThat(file.hunks().get(0).lines()).containsExactly("---- old", "++++ new", " kept");
    }
}
