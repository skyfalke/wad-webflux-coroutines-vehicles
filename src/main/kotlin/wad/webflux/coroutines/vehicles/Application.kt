package wad.webflux.coroutines.vehicles

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
class VehiclesController {
    @Autowired
    private lateinit var rscs: RemoteServiceCallSimulator

    @GetMapping("vehicles/{vin}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getVehicleData(@PathVariable vin: String): Vehicle {
        val basicData = rscs.getBasicData(vin)
        val model = basicData.first
        val engine = basicData.second

        return supervisorScope {
            val pictureDeferred = async {
                val frontView = rscs.getPictures(vin)
                    .firstOrNull { (view, _) ->
                        view == "front"
                    }?.second
                if (frontView != null) {
                    return@async frontView
                }
                println("Fallback Picture")
                rscs.getSilhouette(model)
            }

            val rangeDeferred = async {
                when (engine) {
                    Engine.BEV -> Range(rscs.getElectricRange(vin), null)
                    Engine.PHEV -> {
                        val electricRangeDeferred = async { rscs.getElectricRange(vin) }
                        val fuelRangeDeferred = async { rscs.getFuelRange(vin) }
                        Range(electricRangeDeferred.await(), fuelRangeDeferred.await())
                    }
                    Engine.CEV -> Range(null, rscs.getFuelRange(vin))
                }
            }

            val picture = runCatching {
                pictureDeferred.await()
            }.getOrDefault("")

            val range = runCatching {
                rangeDeferred.await()
            }.getOrDefault(Range())

            Vehicle(vin, model, engine, picture, range)
        }
    }
}

@Service
class RemoteServiceCallSimulator {
    suspend fun getBasicData(vin: String): Pair<String, Engine> {
        delay(1000)
        return "SomeElectricCar" to Engine.BEV
    }

    suspend fun getFuelRange(vin: String): Int {
        delay(1000)
        return 512
    }

    suspend fun getElectricRange(vin: String): Int {
        delay(1000)
        return 512
    }

    suspend fun getPictures(vin: String): Flow<Pair<String, String>> {
        delay(1000)
        return flowOf(
            "top" to "https://example.com/pictures/$vin/top.png",
            "front" to "https://example.com/pictures/$vin/front.png",
            "left" to "https://example.com/pictures/$vin/left.png",
            "right" to "https://example.com/pictures/$vin/right.png"
        )
    }

    suspend fun getSilhouette(vehicleModel: String): String {
        delay(1000)
        return "https://example.com/pictures/$vehicleModel/silhouette.png"
    }
}

data class Vehicle(
    var vin: String,
    var model: String,
    var engine: Engine,
    var picture: String,
    var range: Range
)

data class Range(
    var electric: Int? = null,
    var gasoline: Int? = null
)

enum class Engine {
    BEV, PHEV, CEV
}