.PHONY: build klarinet demo sample docs pages test test-klarinet test-coroutines test-dsp test-android test-ios test-js test-all clean publish

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

pages:
	./gradlew :demo-web:jsBrowserDistribution :dokkaGenerate
	bash ./scripts/assemble-pages.sh

test: test-klarinet

test-klarinet:
	./gradlew :klarinet:allTests

test-coroutines:
	./gradlew :klarinet-coroutines:allTests

test-js:
	./gradlew :klarinet:jsBrowserTest :klarinet-coroutines:jsBrowserTest

test-dsp:
	./gradlew :klarinet:dspTests

test-android:
	./gradlew :klarinet:connectedAndroidDeviceTest

test-ios:
	./gradlew :klarinet:iosSimulatorArm64Test :klarinet:verifyDspEmbeddedInAppleKlibs :klarinet-consumer-test:macosArm64Test

test-all:
	./gradlew :klarinet:allTests :klarinet-coroutines:allTests :klarinet:dspTests :klarinet:iosSimulatorArm64Test :klarinet:verifyDspEmbeddedInAppleKlibs :klarinet-consumer-test:macosArm64Test

clean:
	./gradlew clean

publish:
	./gradlew publishAllPublicationsToMavenCentralRepository
