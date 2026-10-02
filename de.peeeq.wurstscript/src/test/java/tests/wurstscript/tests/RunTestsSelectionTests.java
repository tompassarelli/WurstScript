package tests.wurstscript.tests;

import de.peeeq.wurstio.CompilationProcess;
import de.peeeq.wurstio.WurstCompilerJassImpl;
import de.peeeq.wurstio.languageserver.requests.RequestFailedException;
import de.peeeq.wurstio.languageserver.requests.RunTests;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Optional;

import static org.testng.Assert.*;

public class RunTestsSelectionTests extends WurstScriptTest {
    private WurstCompilerJassImpl compile(WurstGui gui) {
        WurstCompilerJassImpl compiler = new WurstCompilerJassImpl(null, gui, null, new RunArgs());
        WurstModel model = parseFiles(null, Collections.singletonList(new CU("test", """
            package Selection
            @test function firstTest()
                skip
            @test function secondTest()
                skip
            """)), false, compiler);
        compiler.checkProg(model);
        assertTrue(gui.getErrorList().isEmpty(), gui.getErrorList().toString());
        compiler.translateProgToIm(model);
        return compiler;
    }

    @Test
    public void unmatchedExplicitFilterFailsTheRequest() {
        WurstCompilerJassImpl compiler = compile(new WurstGuiCliImpl());
        RunTests runner = new RunTests(Optional.empty(), 0, 0, Optional.empty(), 20,
            Optional.of("firstTest|secondTest"));
        RequestFailedException error = expectThrows(RequestFailedException.class, () ->
            runner.runTests(compiler.getImTranslator(), compiler.getImProg(), Optional.empty(), Optional.empty()));
        assertTrue(error.getMessage().contains("No tests match filter 'firstTest|secondTest'"), error.getMessage());
    }

    @Test
    public void filterStillMatchesCaseInsensitiveLiteralSubstring() {
        WurstCompilerJassImpl compiler = compile(new WurstGuiCliImpl());
        RunTests runner = new RunTests(Optional.empty(), 0, 0, Optional.empty(), 20,
            Optional.of("SELECTION.FIRST"));
        RunTests.TestResult result = runner.runTests(compiler.getImTranslator(), compiler.getImProg(),
            Optional.empty(), Optional.empty());
        assertEquals(result.getTotalTests(), 1);
        assertEquals(result.getPassedTests(), 1);
    }

    @Test
    public void unmatchedFilterIsACompilationErrorForCli() {
        WurstGui gui = new WurstGuiCliImpl();
        WurstCompilerJassImpl compiler = compile(gui);
        CompilationProcess.runTests(gui, compiler, new RunArgs("-testFilter", "missing"));
        assertEquals(gui.getErrorCount(), 1);
        assertTrue(gui.getErrorList().getFirst().getMessage().contains("No tests match filter 'missing'"));
    }
}
