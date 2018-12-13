package bpipe

import org.junit.Test
import static org.junit.Assert.*

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RunnerTest {

    @Test def void testParseParameter() {
        // integer
        def entry = BpipeParamsBinding.parseParam('x=100')
        assertEquals 'x', entry.key
        assertEquals 100, entry.value

        // long
        entry = BpipeParamsBinding.parseParam('y=8223372036854775807')
        assertEquals 'y', entry.key
        assertEquals 8223372036854775807L, entry.value

        // double
        entry = BpipeParamsBinding.parseParam('w=1.2')
        assertEquals 'w', entry.key
        assertEquals 1.2D, entry.value, 0

        // booleans
        entry = BpipeParamsBinding.parseParam('t=True')
        assertEquals 't', entry.key
        assertEquals true, entry.value

        entry = BpipeParamsBinding.parseParam('f=FALSE')
        assertEquals 'f', entry.key
        assertEquals false, entry.value

        // other types as string
        entry = BpipeParamsBinding.parseParam('str=Hello')
        assertEquals 'str', entry.key
        assertEquals "Hello", entry.value

    }

    @Test
    def void testParameterNoValue( ) {

        // parameter with no value specified
        // return 'true' by default
        def entry = BpipeParamsBinding.parseParam('x')
        assertEquals 'x', entry.key
        assertEquals true, entry.value

        entry = BpipeParamsBinding.parseParam('y=')
        assertEquals 'y', entry.key
        assertEquals true, entry.value
    }

    @Test
    def void testParameterNoKey( ) {
        assertNull  BpipeParamsBinding.parseParam('=10')
        assertNull BpipeParamsBinding.parseParam( '' )

    }


    @Test def void testBpipeParamsBinding () {

        BpipeParamsBinding binding = new BpipeParamsBinding()
        binding.setParam("x", 1)
        assertEquals( 1, binding.getVariable('x') )
        // trying to change the value by the setVariable has not effect
        binding.setVariable('x', 2)
        assertEquals( 1, binding.getVariable('x') )

        // using the setParam does change it
        binding.setParam('x',3)
        assertEquals( 3, binding.getVariable('x') )

        // other variables are not affected
        binding.setVariable('z', 'hola')
        assertEquals( 'hola', binding.getVariable('z') )
        binding.setVariable('z', 'hello')
        assertEquals( 'hello', binding.getVariable('z') )


    }

    @Test def void testAddParamers () {


        BpipeParamsBinding binding = new BpipeParamsBinding()
        binding.addParams( [ 'alpha=1', 'beta=2', 'delta=three'] )

        assertEquals( 1, binding.getVariable('alpha') )
        assertEquals( 2, binding.getVariable('beta') )
        assertEquals( 'three', binding.getVariable('delta') )

    }

    @Test
    def void testCliBuilder() {

        def cli = new CliBuilder()
        cli.with {
            p longOpt: 'param', 'defines a pipeline parameter', args: 1, argName: 'param=value', valueSeparator: ',' as char
        }
        def opt = cli.parse( ['-p', 'a=1', '-p', 'b=two', 'pipe-name'] )

        BpipeParamsBinding binding = new BpipeParamsBinding()
        binding.addParams( opt.params )

        assertEquals( 1, binding.getVariable('a') )
        assertEquals( 'two', binding.getVariable('b') )
    }


    @Test
    void testParseMemoryOption() {
        assert Runner.parseMemoryOption("4GB") == 4000
        assert Runner.parseMemoryOption("4gb") == 4000
        assert Runner.parseMemoryOption("4MB") == 4
        assert Runner.parseMemoryOption("4mb") == 4
        assert Runner.parseMemoryOption("4") == 4
    }
    
}
