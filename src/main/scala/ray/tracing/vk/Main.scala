package ray.tracing.vk

import java.io.{FileOutputStream, PrintStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, duration}
import scala.util.{Failure, Random, Success, Try}

object Main {

  val rand: Random = new Random(seed = 101)

  def randomInUnitSphare(): Vec3 = {
    var p: Vec3 = Vec3(0f, 0f, 0f)
    do {
      p = Vec3(
        rand.nextFloat(),
        rand.nextFloat(),
        rand.nextFloat()
      ) * 2.0f - Vec3(1f, 1f, 1f)
    } while (p.squaredLength >= 1.0f)
    p
  }

  def color(r: Ray, hitable: Hitable, depth: Int): Color3 = {

    hitable.hit(r, 0.001f, Float.MaxValue) match {
      case Some(hitRecord) =>
        if (depth < 50) {
          hitRecord.material.scatter(r, hitRecord) match {
            case Some(ScatterRecord(attenuation, scattered)) =>
              val col = color(scattered, hitable, depth + 1)
              Color3(
                col.r * attenuation.x,
                col.g * attenuation.y,
                col.b * attenuation.z
              )
            case None =>
              Color3(0f, 0f, 0f)
          }
        } else {
          Color3(0f, 0f, 0f)
        }

      case None => {
        val unitDirection: Vec3 = r.direction.unitVector
        val t: Float = 0.5f * (unitDirection.y + 1.0f)
        Color3(1f, 1f, 1f) * (1f - t) + Color3(0.5f, 0.7f, 1.0f) * t
      }
    }
  }

  def randomScene(): Hitable = {

    var hittables: List[Hitable] = Nil

    hittables = hittables :+ SphereHitable(
      center = Vec3(0f, -1000f, 0f),
      radius = 1000f,
      material = LambertMaterial(albedo = Vec3(0.5f, 0.5f, 0.5f))
    )
    for {
      a <- -11 until 11
      b <- -11 until 11
    } {
      val chooseMat: Float = rand.nextFloat()
      val center: Vec3 = Vec3(
        a + 0.9f * rand.nextFloat(),
        0.2f,
        b + 0.9f * rand.nextFloat()
      )
      if ((center - Vec3(4f, 0.2f, 0f)).length > 0.9f) {
        if (chooseMat < 0.8f) { // diffuse
          hittables = hittables :+ SphereHitable(
            center = center,
            radius = 0.2f,
            material = LambertMaterial(
              albedo = Vec3(
                rand.nextFloat() * rand.nextFloat(),
                rand.nextFloat() * rand.nextFloat(),
                rand.nextFloat() * rand.nextFloat()
              )
            )
          )
        } else if (chooseMat < 0.95f) { // metal
          hittables = hittables :+ SphereHitable(
            center = center,
            radius = 0.2f,
            material = MetalMaterial(
              albedo = Vec3(
                0.5f * (1 + rand.nextFloat()),
                0.5f * (1 + rand.nextFloat()),
                0.5f * (1 + rand.nextFloat())
              ),
              f = 0.5f * rand.nextFloat()
            )
          )
        } else {
          hittables = hittables :+ SphereHitable(
            center = center,
            radius = 0.2f,
            material = DielectricMaterial(refIdx = 1.5f, rand)
          )
        }
      }
    }

    hittables = hittables :+ SphereHitable(
      center = Vec3(0f, 1f, 0f),
      radius = 1.0f,
      material = DielectricMaterial(
        refIdx = 1.5f,
        rand
      )
    )
    hittables = hittables :+ SphereHitable(
      center = Vec3(-4f, 1f, 0f),
      radius = 1.0f,
      material = LambertMaterial(
        albedo = Vec3(0.4f, 0.2f, 0.1f)
      )
    )
    hittables = hittables :+ SphereHitable(
      center = Vec3(4f, 1f, 0f),
      radius = 1.0f,
      material = MetalMaterial(
        albedo = Vec3(0.7f, 0.6f, 0.5f),
        f = 0.0f
      )
    )

    ListHitable(hittables: _*)
  }

  def iterateRays(
      camera: Camera,
      hitable: Hitable,
      i: Int,
      j: Int,
      nx: Int,
      ny: Int,
      ns: Int
  ): String = {
    var col: Color3 = Color3(0f, 0f, 0f)
    for (s <- 0 until ns) {
      val u: Float = (i + rand.nextFloat()) / nx
      val v: Float = (j + rand.nextFloat()) / ny
      val r: Ray = camera.getRay(rand, u, v)
      col = col + color(r, hitable, 0)
    }
    col = col / ns.toFloat
    col = Color3(
      Math.sqrt(col.r).toFloat,
      Math.sqrt(col.g).toFloat,
      Math.sqrt(col.b).toFloat
    )
    s"${col.ir} ${col.ig} ${col.ib}\n"
  }

  def main(args: Array[String]): Unit = {

    // output
    val out: PrintStream =
      Try(new PrintStream(new FileOutputStream("picture.ppm")))
        .getOrElse(System.out)

    val nx: Int = 1200
    val ny: Int = 800
    val ns: Int = 10

    out.println(s"""P3
         |${nx} ${ny}
         |255""".stripMargin)

    val hitable: Hitable = randomScene()

    val lookfrom: Vec3 = Vec3(13f, 2f, 3f)
    val lookat: Vec3 = Vec3(0f, 0f, 0f)
    val focusDist: Float = 10.0f
    val aperture: Float = 0.1f
    val camera: Camera = Camera(
      lookfrom = lookfrom,
      lookat = lookat,
      vup = Vec3(0f, 1f, 0f),
      vfov = 20f,
      aspect = nx.toFloat / ny,
      aperture = aperture,
      focusDist = focusDist
    )
    val chunkParts = 8
    val futures = for{
      chunk <- 1 to chunkParts
    } yield Future {
      for {
        j <- (ny - 1) * chunk / chunkParts to ny * (chunk - 1) / chunkParts by -1
        i <- 0 until nx
      } yield iterateRays(camera, hitable, i, j, nx, ny, ns)
    }

    Await.ready(futures.map(_.map { resp => resp.flatten}).reduceRight((acc, curr) => {
      for{
        a <- acc
        c <- curr
      } yield c ++ a
    }).flatMap(value => {
      value.foreach(e => out.print(e))
      out.close()
      Future()
    }), Duration.Inf)
  }
}
