# ArrhythmiasClassifierApp


Four Types of Cardiac Arrhythmias : AF, NSR, PAC and PVC are assigned to classify by this app developed with Kotlin in Android Studio. This app is deployed with light weight deep learning neural network MobileNetV2. 
```mermaid
flowchart TD
A[Start]--->B[Initialize parmeters]
B-->C{Image Acquisation}
C--Camera-->D[Permission to access the Camera]
C--Gallery-->E[Permission to access the Gallery]
D--->F[[Image Processing]]
E--->F
F-->G[Classifty the image with deployed .tflite model]
G-->H(End)
```
![IMG_20230110_140214](https://user-images.githubusercontent.com/89783753/211483526-a083716b-2bb5-4519-85d1-108e232b7c2a.jpg)
