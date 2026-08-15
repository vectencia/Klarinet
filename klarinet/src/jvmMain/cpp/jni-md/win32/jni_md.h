#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#ifndef JNIEXPORT
#define JNIEXPORT __declspec(dllexport)
#endif
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall

typedef int jint;
#ifdef _WIN64
typedef long long jlong;
#else
typedef long jlong;
#endif
typedef signed char jbyte;

#endif
