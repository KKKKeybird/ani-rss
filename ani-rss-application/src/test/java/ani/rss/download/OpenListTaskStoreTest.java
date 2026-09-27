package ani.rss.download;

import ani.rss.commons.GsonStatic;
import ani.rss.entity.OpenListTaskInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenListTaskStoreTest {
    @TempDir
    Path directory;

    @Test
    void currentOpenListTaskResponseKeepsFractionalProgressAndLongByteCount() {
        OpenListTaskInfo info = GsonStatic.fromJson("""
                {"id":"task-1","state":1,"progress":37.5,"total_bytes":5368709120}
                """, OpenListTaskInfo.class);
        assertEquals(OpenListTaskInfo.State.Running, info.getState());
        assertEquals(37.5, info.getProgress());
        assertEquals(5_368_709_120L, info.getTotalBytes());
    }

    @Test
    void taskMetadataSurvivesRestartAndTagsAreIdempotent() {
        File path = directory.resolve("cache/openlist-tasks.json").toFile();
        OpenListTaskStore store = new OpenListTaskStore(path);
        store.submitted(new OpenListTaskStore.Task()
                .setId("task-1").setHash("hash-1").setName("Episode 1")
                .setSavePath("/anime").setStagingPath("/anime/.stage")
                .setTags(List.of("ani-rss")));
        OpenListTaskInfo progress = new OpenListTaskInfo()
                .setState(OpenListTaskInfo.State.Running).setProgress(37.5)
                .setTotalBytes(5_368_709_120L);
        store.progress("task-1", progress);
        assertTrue(store.addTag("task-1", "RENAME"));
        assertFalse(store.addTag("task-1", "RENAME"));

        OpenListTaskStore restored = new OpenListTaskStore(path);
        assertEquals(37, restored.get("task-1").getProgress());
        assertEquals(5_368_709_120L, restored.get("task-1").getSize());
        assertEquals(List.of("ani-rss", "RENAME"), restored.get("task-1").getTags());
        restored.completed("task-1", List.of("Episode 1.mkv"), 123L);
        assertTrue(new OpenListTaskStore(path).get("task-1").isCompleted());
    }
}
