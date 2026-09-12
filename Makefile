.PHONY: build klarinet demo sample docs test test-klarinet test-dsp test-android test-ios test-all clean publish

build:
	./gradlew build

klarinet:
	./gradlew :klarinet:build

demo:
	./gradlew :demo-android:assembleDebug

sample:
	./gradlew :sample:run

docs:
	./gradlew :dokkaGenerate

test: test-klarinet

test-klarinet:
	./gradlew :klarinet:allTests

test-dsp:
	./gradlew :klarinet:dspTests

test-android:
	./gradlew :klarinet:connectedAndroidDeviceTest

test-ios:
	./gradlew :klarinet:iosSimulatorArm64Test :klarinet:verifyDspEmbeddedInAppleKlibs :klarinet-consumer-test:macosArm64Test

test-all:
	./gradlew :klarinet:allTests :klarinet:dspTests :klarinet:iosSimulatorArm64Test :klarinet:verifyDspEmbeddedInAppleKlibs :klarinet-consumer-test:macosArm64Test

clean:
	./gradlew clean

publish:
	./gradlew publishAllPublicationsToMavenCentralRepository
