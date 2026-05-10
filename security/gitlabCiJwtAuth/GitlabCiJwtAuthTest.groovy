import spock.lang.Specification

class GitlabCiJwtAuthTest extends Specification {
    def 'not implemented plugin test'() {
        when:
        throw new Exception("Not implemented.")
        then:
        false
    }
}
