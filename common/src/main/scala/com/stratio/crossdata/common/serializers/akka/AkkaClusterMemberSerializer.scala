/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.crossdata.common.serializers.akka

import akka.cluster.{Member, MemberStatus, UniqueAddress}
import akka.cluster.crossdata.builders.MemberBuilder
import org.json4s.JsonAST.{JField, JInt, JObject}
import org.json4s.{CustomSerializer, Extraction}

object AkkaClusterMemberSerializer extends CustomSerializer[Member] ( formats =>
  (
    {
      case JObject(
        List(
          JField("uniqueAddress", jUniqueAddress),
          JField("upNumber", JInt(upNumber)),
          JField("status", jStatus),
          JField("roles", jRoles)
        )
      ) =>

        implicit val _ = formats

        MemberBuilder(
          jUniqueAddress.extract[UniqueAddress],
          upNumber.toInt,
          jStatus.extract[MemberStatus],
          jRoles.extract[List[String]].toSet
        )
    },
    {
      case member: Member =>
        import member._

        implicit val _ = formats

        JObject(
          List(
            JField("uniqueAddress", Extraction.decompose(uniqueAddress)),
            JField("upNumber", JInt(MemberBuilder.extractUpNumber(member))),
            JField("status", Extraction.decompose(status)),
            JField("roles", Extraction.decompose(roles.toList))
          )
        )
    }
    )
)