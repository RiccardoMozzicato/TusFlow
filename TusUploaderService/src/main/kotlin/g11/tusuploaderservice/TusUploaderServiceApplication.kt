package g11.tusuploaderservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TusUploaderServiceApplication

fun main(args: Array<String>) {
    runApplication<TusUploaderServiceApplication>(*args)
}
