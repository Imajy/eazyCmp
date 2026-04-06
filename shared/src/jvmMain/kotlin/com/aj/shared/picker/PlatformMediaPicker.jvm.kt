package com.aj.shared.picker

import androidx.compose.runtime.Composable
import com.aj.shared.ui.AppSnackbarManager
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import java.awt.BorderLayout
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual class PlatformMediaPicker actual constructor() {

    @Composable
    actual fun RegisterLaunchers() {}

    actual fun launch(

        type: PickerType,

        documentConfig: DocumentConfig?,

        onResult: (PickedFile?) -> Unit

    ){

        try{

            when(type){

                PickerType.CAMERA ->

                    openDesktopCamera(onResult)


                PickerType.IMAGE ->

                    openFileChooser(

                        PickerType.IMAGE,

                        onResult

                    )


                PickerType.DOCUMENT ->

                    openFileChooser(

                        PickerType.DOCUMENT,

                        onResult

                    )

            }

        }
        catch(e: Exception){

            e.printStackTrace()

            AppSnackbarManager.show(

                "Unexpected error"

            )

            onResult(null)

        }

    }
}

private fun openFileChooser(

    type: PickerType,

    onResult: (PickedFile?) -> Unit

) {

    try {

        val chooser = JFileChooser()

        chooser.isMultiSelectionEnabled = false

        chooser.fileSelectionMode =
            JFileChooser.FILES_ONLY

        chooser.isAcceptAllFileFilterUsed = false


        val home =
            System.getProperty("user.home")


        val startDir = when(type){

            PickerType.IMAGE ->
                File(home, "Pictures")

            PickerType.DOCUMENT ->
                File(home, "Documents")

            else ->
                File(home)

        }


        if(startDir.exists()){

            chooser.currentDirectory = startDir

        }


        chooser.fileFilter =
            when(type){

                PickerType.IMAGE ->

                    FileNameExtensionFilter(

                        "Images",

                        "jpg",
                        "jpeg",
                        "png",
                        "webp"

                    )


                PickerType.DOCUMENT ->

                    FileNameExtensionFilter(

                        "Documents",

                        "pdf",
                        "doc",
                        "docx"

                    )


                else -> null

            }


        val result =
            chooser.showOpenDialog(null)


        if(result ==
            JFileChooser.APPROVE_OPTION){

            val file =
                chooser.selectedFile


            if(!file.exists()){

                AppSnackbarManager.show("File not found")

                onResult(null)

                return

            }


            val bytes =
                file.readBytes()


            onResult(

                PickedFile(

                    bytes = bytes,

                    fileName = file.name,

                    mimeType =
                        guessMime(file.extension)

                )

            )

        }
        else{

            onResult(null)

        }

    }
    catch (e: Exception){

        e.printStackTrace()

        AppSnackbarManager.show(

            "Unable to open file"

        )

        onResult(null)

    }

}


private fun openDesktopCamera(

    onResult: (PickedFile?) -> Unit

) {

    Thread {

        try {

            val grabber =
                OpenCVFrameGrabber(0)

            grabber.start()


            val converter =
                Java2DFrameConverter()


            val preview =
                JLabel()


            val captureBtn =
                JButton("Capture")


            val cancelBtn =
                JButton("Cancel")


            val panel =
                JPanel(BorderLayout())


            panel.add(

                preview,

                BorderLayout.CENTER

            )


            val buttons =
                JPanel()


            buttons.add(captureBtn)

            buttons.add(cancelBtn)


            panel.add(

                buttons,

                BorderLayout.SOUTH

            )


            val window =
                JFrame("Camera")


            window.contentPane = panel


            window.setSize(600,500)


            window.setLocationRelativeTo(null)


            window.isVisible = true


            var running = true


            Thread{

                while(running){

                    val frame =
                        grabber.grab()


                    val image =
                        converter.convert(frame)


                    if(image != null){

                        SwingUtilities.invokeLater {

                            preview.icon =
                                ImageIcon(image)

                        }

                    }


                    Thread.sleep(30)

                }

            }.start()



            captureBtn.addActionListener{

                try{

                    val frame =
                        grabber.grab()


                    val img =
                        converter.convert(frame)


                    val baos =
                        ByteArrayOutputStream()


                    ImageIO.write(

                        img,

                        "jpg",

                        baos

                    )


                    val bytes =
                        baos.toByteArray()


                    running = false


                    grabber.stop()


                    window.dispose()


                    onResult(

                        PickedFile(

                            bytes = bytes,

                            fileName =
                                "capture.jpg",

                            mimeType =
                                "image/jpeg"

                        )

                    )

                }
                catch(e: Exception){

                    e.printStackTrace()

                    AppSnackbarManager.show(

                        "Camera capture failed"

                    )

                    onResult(null)

                }

            }



            cancelBtn.addActionListener{

                running = false

                grabber.stop()

                window.dispose()

                onResult(null)

            }


        }
        catch(e: Exception){

            e.printStackTrace()

            AppSnackbarManager.show(

                "Camera not available"

            )

            onResult(null)

        }

    }.start()

}


private fun guessMime(ext: String): String {

    return when (ext.lowercase()) {

        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "webp" -> "image/webp"

        "pdf" -> "application/pdf"

        "doc" -> "application/msword"

        "docx" ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        else -> "application/octet-stream"

    }

}