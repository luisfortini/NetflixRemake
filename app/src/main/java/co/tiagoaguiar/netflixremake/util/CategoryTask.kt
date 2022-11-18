package co.tiagoaguiar.netflixremake.util


import android.os.Handler
import android.os.Looper
import android.util.Log
import co.tiagoaguiar.netflixremake.model.Category
import co.tiagoaguiar.netflixremake.model.Movie
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class CategoryTask(private val callback: Callback) {

    //Variavel que vai inserir uma execução na UI-Thread
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    interface Callback {
        fun onPreExecute()
        fun onResult(categories: List<Category>)
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

                if (statusCode > 400) {
                    throw IOException("Erro na comunicação com o servidor!")
                }

                stream = urlConnection.inputStream //sequencia de bytes
                // Forma 1 de ler o JSON - Mais Simples
//
//                val jsonAsString = stream.bufferedReader().use {
//                    it.readText()
//                }

                // Forma 2 de ler o JSON - Mais Simples

                buffer = BufferedInputStream(stream)
                val jsonAsString = toString(buffer)
                val categories = toCategories(jsonAsString)

                handler.post {
                    //aqui está rodando dentro da UI-Thread
                    callback.onResult(categories)
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


    private fun toCategories(jsonAsString: String): List<Category> {

        val categories = mutableListOf<Category>()

        val jsonRoot = JSONObject(jsonAsString)
        val jsonCategories = jsonRoot.getJSONArray("category")
        for (i in 0 until jsonCategories.length()) {
            val jsonCategory = jsonCategories.getJSONObject(i)
            val title = jsonCategory.getString("title")
            val jsonMovies = jsonCategory.getJSONArray("movie")

            val movies = mutableListOf<Movie>()
            for (j in 0 until jsonMovies.length()) {
                val jsonMovie = jsonMovies.getJSONObject(j)
                val id = jsonMovie.getInt("id")
                val coverUrl = jsonMovie.getString("cover_url")

                movies.add(Movie(id, coverUrl))

            }
            categories.add(Category(title, movies))

        }

        return categories
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