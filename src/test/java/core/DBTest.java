package core;

import net.sf.testng.databinding.DataBinding;
import net.sf.testng.databinding.TestInput;
import net.sf.testng.databinding.TestOutput;
import org.testng.annotations.Test;

import static java.lang.Integer.parseInt;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DBTest {

    @DataBinding
    @Test(groups = "csv")
    public void testSumCSV(@TestInput(name = "x") int x, @TestInput(name = "y") int y, @TestOutput(name = "sum") int sum) {
        assertThat(x + y, is(equalTo(sum)));
    }

    @DataBinding
    @Test(groups = "properties")
    public void testSumProperties(@TestInput(name = "x") int x, @TestInput(name = "y") int y, @TestOutput(name = "sum") int sum) {
        assertThat(x + y, is(equalTo(sum)));
    }

    @DataBinding
    @Test(groups = "xml")
    public void testSumXML(@TestInput(name = "x") int x, @TestInput(name = "y") int y, @TestOutput(name = "sum") int sum) {
        assertThat(x + y, is(equalTo(sum)));
    }

    @DataBinding
    @Test(groups = "text")
    public void testSumText(@TestInput(name = "x") String x, @TestInput(name = "y") String y, @TestOutput(name = "sum") String sum) {
        assertThat(parseInt(x) + parseInt(y), is(equalTo(parseInt(sum))));
    }

    @DataBinding
    @Test(groups = "json")
    public void testSumJSON(@TestInput(name = "x") int x, @TestInput(name = "y") int y, @TestOutput(name = "sum") int sum) {
        assertThat(x + y, is(equalTo(sum)));
    }
}