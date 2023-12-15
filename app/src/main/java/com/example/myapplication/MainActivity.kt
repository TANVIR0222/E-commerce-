package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val selectImage = mutableListOf<Uri>()
    private val seclectColur = mutableListOf<Int>()

    private val productStorage = Firebase.storage.reference
    protected val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Product Color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope.let {

                            seclectColur.add(it!!.color)

                            updateColor()

                        }
                    }

                })


                .setNegativeButton("Cancel") { color, _ ->

                    color.dismiss()


                }.show()
        }

        val selectActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

                if (result.resultCode == RESULT_OK) {
                    val intent = result.data

                    // multiple image select

                    if (intent!!.clipData != null) {
                        val count = intent.clipData?.itemCount ?: 0

                        // loop
                        (0 until count).forEach {
                            val imageUrl = intent.clipData?.getItemAt(it)?.uri
                            // null check
                            imageUrl?.let {
                                selectImage.add(it)
                            }
                        }
                    } else {
                        val imageUri = intent.data
                        // null check
                        imageUri?.let {
                            selectImage.add(it)
                        }
                    }
                    updateImages()

                }
            }

        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)

            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"

            selectActivityResult.launch(intent)


        }

    }

    private fun updateImages() {
        binding.tvSelectedImages.text = selectImage.size.toString()
    }

    private fun updateColor() {
        var color = " "
        seclectColur.forEach {
            color = "$color${Integer.toHexString(it)}"

        }
        binding.tvSelectedColors.text = color
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == R.id.saveProduct) {
            val productValidation = validationInformation()
            if (!productValidation) {
                Toast.makeText(this, "Check your input ", Toast.LENGTH_SHORT).show()
                return false
            }

            saveProduct()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun saveProduct() {
        val name = binding.edName.text.toString().trim()
        val category = binding.edCategory.text.toString().trim()
        val price = binding.edPrice.text.toString().trim()
        val offerPercentage = binding.offerPercentage.text.toString().trim()
        val description = binding.edDescription.text.toString().trim()

        val size = getSizeList(binding.edSizes.text.toString().trim())

        val imageUploadDatabase = getimageUploadDatabase()

        val image = mutableListOf<String>()
        // image lancher
        lifecycleScope.launch(Dispatchers.IO) {

            withContext(Dispatchers.Main) {
                showLoading()
            }

            try {
                async {
                    imageUploadDatabase.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {

                            val imageStroge = productStorage.child("Product/image/$id")
                            val resutt = imageStroge.putBytes(it).await()
                            val downloadUrl = resutt.storage.downloadUrl.await().toString()
                            image.add(downloadUrl)


                        }
                    }

                }.await()

            } catch (e: java.lang.Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main){
                    showLoading()
                }

                hideLoding()

            }

            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),

                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (description.isEmpty()) null else description,
                if (seclectColur.isEmpty()) null else seclectColur,
                size,
                image

            )

            firestore.collection("Products").add(product).addOnSuccessListener {
                hideLoding()
            }.addOnFailureListener {
                hideLoding()
                Log.e("error", it.message.toString())


            }


        }
    }

    private fun hideLoding() {

        binding.progress.visibility = View.INVISIBLE
    }

    private fun showLoading() {

        binding.progress.visibility = View.VISIBLE


    }

    private fun getimageUploadDatabase(): List<ByteArray> {
        val imageByteArray = mutableListOf<ByteArray>()
        selectImage.forEach {
            val stream = ByteArrayOutputStream()
            val imageBMP = MediaStore.Images.Media.getBitmap(contentResolver, it)

            if (imageBMP.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                imageByteArray.add(stream.toByteArray())
            }


        }

        return imageByteArray

    }

    // s,M,L,xL
    private fun getSizeList(sizeString: String): List<String>? {

        if (sizeString.isEmpty())
            return null
        val sizeList = sizeString.split(", ")
        return sizeList


    }

    private fun validationInformation(): Boolean {

        if (binding.edPrice.text.toString().trim().isEmpty())
            return false
        if (binding.edName.text.toString().trim().isEmpty())
            return false
        if (binding.edCategory.text.toString().trim().isEmpty())
            return false

        if (selectImage.isEmpty())
            return false

        return true


    }
}