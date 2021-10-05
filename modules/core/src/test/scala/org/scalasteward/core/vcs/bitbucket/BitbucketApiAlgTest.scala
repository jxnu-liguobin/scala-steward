package org.scalasteward.core.vcs.bitbucket

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.literal._
import munit.FunSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git._
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._

class BitbucketApiAlgTest extends FunSuite {
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "repositories" / "fthomas" / "base.g8" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "fthomas"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/fthomas/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case GET -> Root / "repositories" / "scala-steward" / "base.g8" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "scala-steward"
          },
          "parent": {
              "full_name": "fthomas/base.g8"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "master" =>
      Ok(
        json"""{
          "name": "master",
          "target": {
              "hash": "07eb2a203e297c8340273950e98b2cab68b560c1"
          }
        }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "custom" =>
      Ok(
        json"""{
          "name": "custom",
          "target": {
              "hash": "12ea4559063c74184861afece9eeff5ca9d33db3"
          }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "forks" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
            "type": "branch",
            "name": "master"
          },
          "owner": {
            "nickname": "scala-steward"
          },
          "parent": {
            "full_name": "fthomas/base.g8"
          },
          "links": {
            "clone": [
                {
                    "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                    "name": "https"
                }
            ]
          }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
      Ok(
        json"""{
            "id": 2,
            "title": "scala-steward-pr",
            "state": "OPEN",
            "links": {
                "html": {
                    "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                }
            }
          }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
      Ok(
        json"""{
          "values": [
              {
                  "id": 2,
                  "title": "scala-steward-pr",
                  "state": "OPEN",
                  "links": {
                      "html": {
                          "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                      }
                  }
              }
          ]
      }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" / IntVar(
          _
        ) / "decline" =>
      Ok(
        json"""{
            "id": 2,
            "title": "scala-steward-pr",
            "state": "DECLINED",
            "links": {
                "html": {
                    "href": "https://bitbucket.org/fthomas/base.g8/pullrequests/2"
                }
            }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" /
        IntVar(_) / "comments" =>
      Created(json"""{
                  "content": {
                      "raw": "Superseded by #1234"
                  }
          }""")
  }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  private val bitbucketApiAlg = new BitbucketApiAlg[IO](
    config.vcsApiHost,
    AuthenticatedUser("scala-steward", ""),
    _ => IO.pure,
    false
  )

  private val prUrl = uri"https://bitbucket.org/fthomas/base.g8/pullrequests/2"
  private val repo = Repo("fthomas", "base.g8")
  private val master = Branch("master")
  private val custom = Branch("custom")
  private val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://scala-steward@bitbucket.org/fthomas/base.g8.git",
    master
  )

  private val parentWithCustomDefaultBranch = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://scala-steward@bitbucket.org/fthomas/base.g8.git",
    custom
  )

  private val fork = RepoOut(
    "base.g8",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
    master
  )

  private val forkWithCustomDefaultBranch = RepoOut(
    "base.g8",
    UserOut("scala-steward"),
    Some(parentWithCustomDefaultBranch),
    uri"https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
    custom
  )

  private val defaultBranch = BranchOut(
    master,
    CommitOut(Sha1(HexString.unsafeFrom("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  private val defaultCustomBranch = BranchOut(
    custom,
    CommitOut(Sha1(HexString.unsafeFrom("12ea4559063c74184861afece9eeff5ca9d33db3")))
  )

  private val pullRequest =
    PullRequestOut(prUrl, PullRequestState.Open, PullRequestNumber(2), "scala-steward-pr")

  test("createForkOrGetRepo") {
    val repoOut = bitbucketApiAlg.createForkOrGetRepo(repo, doNotFork = false).unsafeRunSync()
    assertEquals(repoOut, fork)
  }

  test("createForkOrGetRepo without forking") {
    val repoOut = bitbucketApiAlg.createForkOrGetRepo(repo, doNotFork = true).unsafeRunSync()
    assertEquals(repoOut, parent)
  }

  test("createForkOrGetRepoWithDefaultBranch") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithDefaultBranch(repo, doNotFork = false, defaultBranch = None)
        .unsafeRunSync()
    assertEquals(repoOut, fork)
    assertEquals(branchOut, defaultBranch)
  }

  test("createForkOrGetRepoWithDefaultBranch with custom default branch") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithDefaultBranch(repo, doNotFork = false, defaultBranch = Some(custom))
        .unsafeRunSync()
    assertEquals(repoOut, forkWithCustomDefaultBranch)
    assertEquals(branchOut, defaultCustomBranch)
  }

  test("createForkOrGetRepoWithDefaultBranch without forking") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithDefaultBranch(repo, doNotFork = true, defaultBranch = None)
        .unsafeRunSync()
    assertEquals(repoOut, parent)
    assertEquals(branchOut, defaultBranch)
  }

  test("createForkOrGetRepoWithDefaultBranch without forking with custom default branch") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithDefaultBranch(repo, doNotFork = true, defaultBranch = Some(custom))
        .unsafeRunSync()
    assertEquals(repoOut, parentWithCustomDefaultBranch)
    assertEquals(branchOut, defaultCustomBranch)
  }

  test("createPullRequest") {
    val data = NewPullRequestData(
      "scala-steward-pr",
      "body",
      "master",
      master
    )
    val pr = bitbucketApiAlg.createPullRequest(repo, data).unsafeRunSync()
    assertEquals(pr, pullRequest)
  }

  test("listPullRequests") {
    val prs = bitbucketApiAlg.listPullRequests(repo, "master", master).unsafeRunSync()
    assertEquals(prs, List(pullRequest))
  }

  test("closePullRequest") {
    val pr = bitbucketApiAlg.closePullRequest(repo, PullRequestNumber(1)).unsafeRunSync()
    assertEquals(pr, pr.copy(state = PullRequestState.Closed))
  }

  test("commentPullRequest") {
    val comment = bitbucketApiAlg
      .commentPullRequest(repo, PullRequestNumber(1), "Superseded by #1234")
      .unsafeRunSync()
    assertEquals(comment, Comment("Superseded by #1234"))
  }
}
