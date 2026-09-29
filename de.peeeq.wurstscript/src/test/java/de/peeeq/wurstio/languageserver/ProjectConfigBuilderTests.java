package de.peeeq.wurstio.languageserver;

import de.peeeq.wurstio.map.importer.ImportFile;
import de.peeeq.wurstio.languageserver.requests.MapRequest;
import de.peeeq.wurstio.mpq.MpqEditor;
import de.peeeq.wurstio.mpq.MpqEditorFactory;
import de.peeeq.wurstio.utils.W3InstallationData;
import de.peeeq.wurstscript.RunArgs;
import net.moonlightflower.wc3libs.port.GameVersion;
import net.moonlightflower.wc3libs.bin.app.W3I;
import org.wurstscript.projectconfig.WurstProjectConfigData;
import org.wurstscript.projectconfig.WurstProjectConfigReader;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ProjectConfigBuilderTests {
    private static final String LEGACY_CONFIG_HASH_BEFORE_INJECTION_VERSION = "f1fbbe5a7ebd559638e33f2a42435976";

    @Test
    public void pinnedPatchSelectsNewestSupportedW3iFormat() throws Exception {
        assertW3IVersion("1.30", W3I.EncodingFormat.W3I_0x19.getVersion());
        assertW3IVersion("1.31", W3I.EncodingFormat.W3I_0x1C.getVersion());
        assertW3IVersion("1.32", W3I.EncodingFormat.W3I_0x1F.getVersion());
        assertW3IVersion("2.0", W3I.EncodingFormat.W3I_0x21.getVersion());
        assertW3IVersion("3.0", W3I.EncodingFormat.W3I_0x27.getVersion());
    }

    @Test
    public void noPinnedPatchPreservesSourceW3iVersion() throws Exception {
        W3I w3i = new W3I();
        w3i.setFileVersion(W3I.EncodingFormat.W3I_0x27.getVersion());

        ProjectConfigBuilder.applyW3IVersion(WurstBuildConfig.empty(), w3i, false);

        assertEquals(w3i.getFileVersion(), W3I.EncodingFormat.W3I_0x27.getVersion());
    }

    @Test
    public void readsW3iFromSourceMapInsteadOfDowngradedCache() throws Exception {
        Path sourceMap = Files.createTempDirectory("source-map");
        Path sourceW3i = sourceMap.resolve("war3map.w3i");
        W3I original = new W3I();
        original.setFileVersion(W3I.EncodingFormat.W3I_0x27.getVersion());
        original.write(sourceW3i.toFile());

        W3I loaded = ProjectConfigBuilder.readW3I(sourceMap.toFile());
        ProjectConfigBuilder.applyW3IVersion(WurstBuildConfig.empty(), loaded, false);

        assertEquals(loaded.getFileVersion(), W3I.EncodingFormat.W3I_0x27.getVersion());
    }

    @Test
    public void luaKeepsScriptLanguageFieldInOlderSourceW3i() throws Exception {
        W3I w3i = new W3I();
        w3i.setFileVersion(W3I.EncodingFormat.W3I_0x19.getVersion());

        ProjectConfigBuilder.applyW3IVersion(WurstBuildConfig.empty(), w3i, true);

        assertEquals(w3i.getFileVersion(), W3I.EncodingFormat.W3I_0x1F.getVersion());
        assertEquals(w3i.getScriptLang(), W3I.ScriptLang.LUA);
    }

    @Test
    public void configuredPlayersAndMapNameReachScriptAndW3iInSameBuild() throws Exception {
        Path fixture = Path.of(getClass().getResource("project-config-order/wurst.build").toURI()).getParent();
        Path project = Files.createTempDirectory("project-config-order");
        Path buildDir = Files.createDirectory(project.resolve("_build"));
        Path targetMap = project.resolve("target.w3x");
        Path sourceMap = Files.createDirectory(project.resolve("source-map"));
        Files.copy(fixture.resolve("legacy-config-cache.w3x"), targetMap);
        Files.copy(fixture.resolve("wurst.build"), project.resolve(ProjectConfigBuilder.FILE_NAME));
        Path mapScript = project.resolve("war3map.j");
        Files.copy(fixture.resolve("war3map.j"), mapScript);
        Path cachedScript = buildDir.resolve(MapRequest.BUILD_CONFIGURED_SCRIPT_NAME);
        Files.copy(fixture.resolve("before/01_war3mapj_with_config.j.txt"), cachedScript);
        Files.setLastModifiedTime(cachedScript, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
        WurstProjectConfigData config = WurstProjectConfigReader.load(project.resolve(ProjectConfigBuilder.FILE_NAME));

        W3I sourceW3i = new W3I();
        sourceW3i.setFileVersion(W3I.EncodingFormat.W3I_0x27.getVersion());
        sourceW3i.setMapName("Old Map Name");
        W3I.Player oldPlayer = new W3I.Player();
        oldPlayer.setNum(0);
        oldPlayer.setName("Old Player Name");
        sourceW3i.addPlayer(oldPlayer);
        sourceW3i.write(sourceMap.resolve("war3map.w3i").toFile());

        try (MpqEditor mpq = MpqEditorFactory.getEditor(Optional.of(targetMap.toFile()))) {
            ImportFile.CacheManifest manifest = new ImportFile.CacheManifest();
            manifest.setMapConfig(LEGACY_CONFIG_HASH_BEFORE_INJECTION_VERSION);
            ImportFile.saveManifest(mpq, manifest);
            assertTrue(ImportFile.getCachedManifest(mpq).orElseThrow()
                .mapConfigMatches(LEGACY_CONFIG_HASH_BEFORE_INJECTION_VERSION));
        }

        var result = ProjectConfigBuilder.apply(
            config,
            targetMap.toFile(),
            sourceMap.toFile(),
            mapScript.toFile(),
            buildDir.toFile(),
            RunArgs.defaults(),
            new W3InstallationData(Optional.empty(), Optional.of(new GameVersion("3.0"))),
            MapRequest.BUILD_CONFIGURED_SCRIPT_NAME
        );

        String configuredScript = Files.readString(result.script.toPath());
        assertTrue(configuredScript.contains("call SetPlayers(2)"), configuredScript);
        assertTrue(configuredScript.contains("Lobby Config Probe"), configuredScript);

        W3I configuredW3i = new W3I(Files.readAllBytes(result.w3i.toPath()));
        assertEquals(configuredW3i.getMapName(), "Lobby Config Probe");
        assertEquals(configuredW3i.getPlayers().size(), 2);
        assertEquals(configuredW3i.getPlayers().get(0).getName(), "Fighter 1");
        assertEquals(configuredW3i.getPlayers().get(1).getName(), "Fighter 2");
    }

    private static void assertW3IVersion(String patch, int expected) throws Exception {
        Path project = Files.createTempDirectory("w3i-version");
        Files.writeString(project.resolve(ProjectConfigBuilder.FILE_NAME), "wc3Patch: " + patch + "\n");
        WurstBuildConfig config = WurstBuildConfig.fromWorkspaceRoot(WFile.create(project.toFile()));
        W3I w3i = new W3I();
        w3i.setFileVersion(W3I.EncodingFormat.W3I_0x27.getVersion());

        ProjectConfigBuilder.applyW3IVersion(config, w3i, false);

        assertEquals(w3i.getFileVersion(), expected, "unexpected W3I format for patch " + patch);
    }
}
