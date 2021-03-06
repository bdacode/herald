package herald

import unfiltered.request._
import unfiltered.response._

import com.ning.http.client.oauth.RequestToken

import dispatch.Promise

object Preview {
  private var requestToken = Option.empty[RequestToken]
  def apply(body: => Either[String,Seq[xml.Node]],
            title: => Either[String,String]) {
    unfiltered.netty.Http.anylocal.plan(unfiltered.netty.cycle.Planify {
      case req @ Host(host) =>
        val auth = new Auth("http://%s%s".format(host, req.uri))
        req match {
          case GET(Path(Seg(Nil))) & Params(Verifier(verifier)) =>
            completeExchange(auth, verifier)

          case GET(Path(Seg(Nil))) =>
            foldError(
              for (html <- previewContent(body, title).right)
                yield Html(html)
            )

          case POST(Path(Seg(Nil))) =>
            Herald.accessToken.fold(
              _ => startExchange(auth),
              tok => publish(body, auth, tok)
            )

          case _ => Pass
        }
    }).run { server =>
      unfiltered.util.Browser.open(
        "http://127.0.0.1:%d/".format(server.port)
      )
      println("\nPreviewing notes. Press CTRL+C to stop.")
    }
  }

  def startExchange(auth: Auth) =
    auth.fetchRequestToken().fold(
      err => ServiceUnavailable ~> ResponseString(err),
      tok => {
        requestToken = Some(tok)
        Redirect(auth.signedAuthorize(tok))
      }
    )

  def completeExchange(auth: Auth, verifier: String) = {
    val access = requestToken.toRight {
      "Unexpected state, start over and try again"
    }.right.map { tok =>
      auth.fetchAccessToken(tok, verifier)()
    }.joinRight
    foldError(for {
      accessToken <- access.right
      props <- Herald.heraldProperties.right
      path <- Herald.heraldCredentialsPath.right
    } yield {
      props
        .set("oauth_token", accessToken.getKey)
        .set("oauth_token_secret", accessToken.getSecret)
        .save(path)
      Redirect("/")
    })
  }

  def publish(body: Either[String,Seq[xml.Node]],
              auth: Auth,
              accessToken: RequestToken) = {
    (for {
      bodyXml <- body.right
      title <- Herald.title.right
    } yield for {
      _ <- allowedResponse(accessToken,
                           Herald.tumblrName).right
      url <- errorMap(Publish(bodyXml,
                              accessToken,
                              Herald.tumblrHostname,
                              title,
                              Herald.name)).right
    } yield Redirect(url)).left.map(
      serverError
    ).right.map { _() }.joinRight.fold(identity,identity)
  }

  def errorMap[R](eth: Promise[Either[String, R]]) =
    eth.left.map(serverError)
  def serverError(err: String) =
    InternalServerError ~> ResponseString(err)
  def foldError[R <: ResponseFunction[Any]](eth: Either[String,R]) =
    eth.fold(serverError, identity)

  def allowedResponse(accessToken: RequestToken, tumblrName: String) =
    errorMap(
      Publish.allowed(accessToken, tumblrName)
    ).map { _.right.flatMap { allowed =>
      if (allowed) Right(true)
      else Left(
        Html(
          <html>
          <p>This account is not yet allowed to post to {tumblrName}.</p>
          <p><a href={
            "mailto:%s?subject=implicit.ly%%20invite".format(
              Herald.adminEmail)
          }>Request an invite</a>, with a link to one or two
          of your Scala projects.</p>
          </html>
        )
      )
    } }

  def previewContent(bodyContent: Either[String, Seq[xml.Node]],
                     title: Either[String, String]) = 
    for {
      body <- bodyContent.right
      t <- title.right
    } yield
      <html>
      <head>
        <title> { t } </title>
        <style> {"""
          div.about * { font-style: italic }
          div.about em { font-style: normal }
        """} </style>
      </head>
      <body>
        <form action="/" method="POST"><div>{
          Herald.accessToken.fold(
            _ => <input type="submit" value="Authorize with Tumblr" />
            ,
            _ => <input type="submit" value="Publish Notes" />
          )
        }</div></form>
        <h2><a href="#">{ t }</a></h2>
        { body }
      </body>
      </html>
}
object Verifier extends Params.Extract("oauth_verifier", Params.first)
