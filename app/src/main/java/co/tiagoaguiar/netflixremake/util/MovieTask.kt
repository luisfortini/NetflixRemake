package co.tiagoaguiar.netflixremake.util


import android.os.Handler
import android.os.Looper
import android.util.Log
import co.tiagoaguiar.netflixremake.model.Category
import co.tiagoaguiar.netflixremake.model.Movie
import co.tiagoaguiar.netflixremake.model.MovieDetail
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MovieTask(private val callback: Callback) {

    //Variavel que vai inserir uma execução na UI-Thread
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    interface Callback {
        fun onPreExecute()
        fun onResult(movieDetail: MovieDetail)
        fun onFailure(message: String)
    }

    fun execute(url: String) {

        callback.onPreExecute()
        //Ainda está utilizando a UI Thread

        executor.execute {
            //Aqui estamos uilizando uma nova Thread [Processo paralelo]

            var urlConnection: HttpsURLConnection? = null
            var buffer: BufferedInputStream? = null
            var stream: InputStream? = null

            try {
                val requestURL = URL(url) //abrir uma URL
                urlConnection =
                    requestURL.openConnection() as HttpsURLConnection // abrir conexão
                urlConnection.readTimeout = 2000 //tempo máximo para leitura (2s)
                urlConnection.connectTimeout = 2000  //tempo máximo para conectar (2s)

                val statusCode = urlConnection.responseCode //Código da resposta

                if (statusCode == 400) {
                    stream = urlConnection.errorStream
                    buffer = BufferedInputStream(stream)
                    val jsonAsString = toString(buffer)
                    val json = JSONObject(jsonAsString)
                    val messageError = json.getString("message")
                    throw IOException(messageError)

                } else if (statusCode > 400) {
                    throw IOException("Erro na comunicação com o servidor!")
                }

                stream = urlConnection.inputStream //sequencia de bytes

                buffer = BufferedInputStream(stream)
                val jsonAsString = toString(buffer)
                val movieDetail = toMovieDetail(jsonAsString)

                handler.post {
                    //aqui está rodando dentro da UI-Thread
                    callback.onResult(movieDetail)
                }


            } catch (e: IOException) {

                val message = e.message ?: "Erro desconhecido"
                Log.e("Teste", message, e)
                handler.post {
                    callback.onFailure(message)
                }


            } finally {
                urlConnection?.disconnect()
                buffer?.close()
                stream?.close()
            }

        }
    }


    private fun toMovieDetail(jsonAsString: String): MovieDetail {

        val json = JSONObject(jsonAsString)

        val id = json.getInt("id")
        val title = json.getString("title")
        val desc = json.getString("desc")
        val cast = json.getString("cast")
        val coverUrl = json.getString("cover_url")
        val jsonListMovie = json.getJSONArray("movie")


        val movieDetail = Movie(id, coverUrl, title, desc, cast)

        val similars = mutableListOf<Movie>()
        similars.clear()
        for (i in 0 until jsonListMovie.length()) {

            val jsonMovie = jsonListMovie.getJSONObject(i)
            val similaId = jsonMovie.getInt("id")
            val similarCoverUrl = jsonMovie.getString("cover_url")
            similars.add(Movie(similaId, similarCoverUrl))
        }


        return MovieDetail(movieDetail, similars)

    }

    private fun toString(stream: InputStream): String {
        val bytes = ByteArray(1024)
        val baos = ByteArrayOutputStream()
        var read: Int
        while (true) {
            read = stream.read(bytes)
            if (read <= 0) {
                break
            }
            baos.write(bytes, 0, read)
        }
        return String(baos.toByteArray())
    }
}