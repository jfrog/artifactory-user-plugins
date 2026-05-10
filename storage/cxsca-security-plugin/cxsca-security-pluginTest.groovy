import spock.lang.Specification

class cxsca-security-pluginTest extends Specification {
    def 'not implemented plugin test'() {
        when:
        throw new Exception("Not implemented.")
        then:
        false
    }
}