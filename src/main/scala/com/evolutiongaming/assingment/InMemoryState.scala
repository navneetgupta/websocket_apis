package com.evolutiongaming.assingment

import cats.implicits._

object InMemoryState {
  val allowedUsers = List(User("user1234", "password1234", UserT), User("admin", "admin", AdminT))
  def apply(): InMemoryState = InMemoryState(Nil, Nil, allowedUsers, Map.empty)
}
case  class InMemoryState(subscribers: List[(String,UserType)], tables: List[table], allowedUsers: List[User], connected: Map[String, (String, UserType)]) {
  def processMessage(req: UserReqWrapper): (InMemoryState, Seq[UserReqWrapper]) = req.msg match {
    case login(us, ps) =>
      val filteredUser  = allowedUsers.filter(u => (u.username == us && u.password == ps))
      connected.get(us) match {
        case Some(uuid) => (this, Seq(UserReqWrapper(req.user, login_successful(filteredUser.head.userType))))
        case _ =>if(filteredUser.size > 0)
          (this.copy(connected = connected + (us -> (req.user, filteredUser.head.userType))), Seq(UserReqWrapper(req.user, login_successful(filteredUser.head.userType))))
        else
          (this, Seq(UserReqWrapper(req.user, login_failed)))
      }
    case ping(seq) =>
      (this, Seq(UserReqWrapper(req.user, pong(seq))))
    case `subscribe_tables` =>
      connected.find(_._2._1 === req.user).fold((this, Seq(UserReqWrapper(req.user,not_authorized))))(a => {
        subscribers.find(_._1 == a._1).fold((this.copy(subscribers = (a._1, a._2._2) +:this.subscribers ), Seq(UserReqWrapper(req.user,table_list(tables)))))( _ =>
          (this, Seq(UserReqWrapper(req.user,table_list(tables)))))
      })
    case `unsubscribe_tables` =>
      connected.find(_._2._1 === req.user).fold((this, Seq(UserReqWrapper(req.user,not_authorized))))(a => {
        subscribers.find(_._1 == a._1).fold((this, Seq(UserReqWrapper(req.user,table_list(tables)))))( v =>
          (this.copy(subscribers = subscribers.filter(_._1 =!= a._1)), Seq(UserReqWrapper(req.user,table_list(tables)))))
      })
    case add_table(after_id,table) =>
      // Not Sure What after_id means and case seems to be ambigious So Taking simle case
      // after_id < 0 add_failes
      // after_id > 0 add_to table with id = biggest_id_exisiting + 1
      connected.find(_._2._1 === req.user).fold((this, Seq(UserReqWrapper(req.user,not_authorized))))(a => {
        if(a._2._2 != AdminT) {
          (this, Seq(UserReqWrapper(req.user, not_authorized)))
        } else {
          if(after_id < 0) (this, Seq(UserReqWrapper(req.user, add_failed(after_id))))
          else {
            if(tables.isEmpty)
              (this.copy(tables = table.copy(id = 1.some):: tables), subscribers.map(s => UserReqWrapper(connected.get(s._1).get._1, table_added(after_id,table.copy(id = 1.some)))))
            else {
              val biggestId = tables.sortWith((t1,t2) => t1.id.get > t2.id.get).head.id
              val idToAdd = biggestId.map(_ + 1)
              (this.copy(tables = table.copy(id = idToAdd):: tables), subscribers.map(s => UserReqWrapper(connected.get(s._1).get._1, table_added(after_id,table.copy(id = idToAdd)))))
            }
          }
        }
      })
    case update_table(table) =>
      connected.find(_._2._1 === req.user).fold((this, Seq(UserReqWrapper(req.user,not_authorized))))(a => {
        if(a._2._2 != AdminT) {
          (this, Seq(UserReqWrapper(req.user, not_authorized)))
        } else {
          val filteredTable = tables.filter(_.id =!= table.id)
          tables.find(_.id === table.id).fold((this, Seq(UserReqWrapper(req.user, update_failed(table.id.get)))))( _ =>
            (this.copy(tables = table :: filteredTable), subscribers.map(s => UserReqWrapper(connected.get(s._1).get._1, table_updated(table))))
          )
        }
      })
    case remove_table(id) =>
      connected.find(_._2._1 === req.user).fold((this, Seq(UserReqWrapper(req.user,not_authorized))))(a => {
        if(a._2._2 != AdminT) {
          (this, Seq(UserReqWrapper(req.user, not_authorized)))
        } else {
          val filteredTable = tables.filter(_.id =!= id.some)
          tables.find(_.id === id.some).fold((this, Seq(UserReqWrapper(req.user, removal_failed(id)))))( _ =>
            (this.copy(tables = filteredTable), subscribers.map(s => UserReqWrapper(connected.get(s._1).get._1, table_removed(id))))
          )
        }
      })
    case _ =>
      println(req)
      println(subscribers)
      (this, Seq(UserReqWrapper(req.user, UnexpectedFailure("Invalid Message"))))
  }


}