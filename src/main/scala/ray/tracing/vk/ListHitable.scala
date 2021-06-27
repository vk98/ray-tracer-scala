package ray.tracing.vk

case class ListHitable(hitables: Hitable*) extends Hitable {
  override def hit(r: Ray, tMin: Float, tMax: Float): Option[HitRecord] = {
    var closestSoFar: Float = tMax
    var hitRecordOpt: Option[HitRecord] = None
    for (hitable <- hitables) {
      hitable.hit(r, tMin, closestSoFar) match {
        case Some(hitRecord) => {
          closestSoFar = hitRecord.t
          hitRecordOpt = Some(hitRecord)
        }
        case None => {}
      }
    }

    hitRecordOpt
  }
}
