package dev.xingguang.terminalblade.combat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MistletoeModelGroupsTest {
    private static final List<String> REQUIRED = List.of(
            "blade",
            "blade_luminous",
            "blade_damaged",
            "blade_damaged_luminous",
            "sheath",
            "sheath_luminous",
            "item_blade",
            "item_blade_luminous",
            "item_damaged",
            "item_damaged_luminous",
            "item_bladens",
            "item_bladens_luminous"
    );

    @Test
    void objDeclaresEveryRendererGroup() throws Exception {
        Path model = Path.of("src/main/resources/assets/terminalblade/model/named/mistletoe.obj");
        Set<String> groups = new HashSet<>();
        for (String line : Files.readAllLines(model)) {
            if (line.startsWith("g ")) {
                groups.add(line.substring(2).trim());
            }
        }
        for (String group : REQUIRED) {
            assertTrue(groups.contains(group), "缺少 OBJ 组 " + group);
        }
    }
}
