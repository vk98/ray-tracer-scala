package ray.tracing.vk

case class HitRecord(t: Float, p: Vec3, normal: Vec3, material: Material)

abstract class Hitable {
  def hit(r: Ray, tMin: Float, tMax: Float): Option[HitRecord]
}
