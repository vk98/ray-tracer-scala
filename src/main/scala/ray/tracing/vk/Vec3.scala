package ray.tracing.vk

case class Vec3(x: Float, y: Float, z: Float) {

  lazy val length: Float = Math.sqrt(squaredLength).toFloat

  lazy val squaredLength: Float = x * x + y * y + z * z

  lazy val unitVector: Vec3 = this / length

  def +(that: Vec3): Vec3 = Vec3(x + that.x, y + that.y, z + that.z)

  def unary_- : Vec3 = Vec3(-x, -y, -z)

  def -(that: Vec3): Vec3 = this + (-that)

  def *(t: Float): Vec3 = Vec3(x * t, y * t, z * t)

  def /(t: Float): Vec3 = Vec3(x / t, y / t, z / t)

  def dot(that: Vec3): Float = x * that.x + y * that.y + z * that.z

  def cross(that: Vec3): Vec3 =
    Vec3(
      y * that.z - that.y * z,
      z * that.x - that.z * x,
      x * that.y - that.x * y
    )

}
