AC_INIT([sugarmaker], [2.5.1-sugar4])
AC_PREREQ([2.59])

# Allow legacy macro usage under modern autoconf
m4_pattern_allow([AC_MSG_ERROR])

AC_CANONICAL_HOST
AC_CONFIG_SRCDIR([cpu-miner.c])
AC_CONFIG_HEADERS([cpuminer-config.h])

AM_INIT_AUTOMAKE([foreign])
AM_MAINTAINER_MODE

EXTERNAL_CFLAGS="$CFLAGS"

# ======================
# Compiler checks
# ======================
AC_PROG_CC
AC_PROG_GCC_TRADITIONAL
AM_PROG_CC_C_O
AM_PROG_AS
AC_PROG_RANLIB

if test -n "$EXTERNAL_CFLAGS"; then
  CFLAGS="$EXTERNAL_CFLAGS"
else
  CFLAGS='-Wall -O2 -fomit-frame-pointer'
fi

# ======================
# Header checks
# ======================
AC_CHECK_HEADERS([sys/endian.h sys/param.h syslog.h])

AC_CHECK_HEADERS([sys/sysctl.h], [], [], [
  #include <sys/types.h>
  #ifdef HAVE_SYS_PARAM_H
  #include <sys/param.h>
  #endif
])

AC_CHECK_DECLS([be32dec, le32dec, be32enc, le32enc], [], [], [
  AC_INCLUDES_DEFAULT
  #ifdef HAVE_SYS_ENDIAN_H
  #include <sys/endian.h>
  #endif
])

AC_FUNC_ALLOCA
AC_CHECK_FUNCS([getopt_long])

PTHREAD_FLAGS="-pthread"
WS2_LIBS=""
have_win32=false

# ======================
# User options (MUST be top-level)
# ======================
AC_ARG_WITH([curl],
  [  --with-curl=PATH     prefix where curl is installed [default=/usr]])

AC_ARG_WITH([crypto],
  [  --with-crypto=PATH   prefix where openssl crypto is installed [default=/usr]])

# ======================
# Platform-specific logic
# ======================
case $host in

  *-*-mingw*)
    have_win32=true
    PTHREAD_FLAGS=""
    WS2_LIBS="-lws2_32"
    ;;

  *linux*)
    if test -z "$LIBCURL"; then
      LIBCURL="-lcurl"
    fi
    ;;

  *-apple-*)
    if test -z "$LIBCURL"; then
      LIBCURL="-lcurl"
    fi
    ;;
esac

# ======================
# libcurl / crypto flags
# ======================
if test -n "$with_curl"; then
  LIBCURL_CFLAGS="-I$with_curl/include"
  LIBCURL_CPPFLAGS="-I$with_curl/include"
  LIBCURL_LDFLAGS="-L$with_curl/lib"
fi

if test -n "$with_crypto"; then
  LIBCURL_CFLAGS="$LIBCURL_CFLAGS -I$with_crypto/include"
  LIBCURL_CPPFLAGS="$LIBCURL_CPPFLAGS -I$with_crypto/include"
  LIBCURL_LDFLAGS="-L$with_crypto/lib $LIBCURL_LDFLAGS"
  LIBCURL="$LIBCURL -lssl -lcrypto"
fi

CFLAGS="$CFLAGS $LIBCURL_CFLAGS"
CPPFLAGS="$CPPFLAGS $LIBCURL_CPPFLAGS"
LDFLAGS="$LDFLAGS $LIBCURL_LDFLAGS"

AC_SUBST(LIBCURL)

# ======================
# libcurl (portable)
# ======================
AC_CHECK_HEADERS([curl/curl.h], ,
  [AC_MSG_ERROR([libcurl headers not found])])

AC_CHECK_LIB([curl], [curl_easy_init],
  [
    LIBS="$LIBS -lcurl"
  ],
  [AC_MSG_ERROR([libcurl library not found])])


# ======================
# Jansson
# ======================
AC_CHECK_LIB(jansson, json_loads,
  request_jansson=false,
  request_jansson=true)

AM_CONDITIONAL([WANT_JANSSON], [test x$request_jansson = xtrue])
AM_CONDITIONAL([HAVE_WINDOWS], [test x$have_win32 = xtrue])

if test x$request_jansson = xtrue; then
  JANSSON_LIBS="compat/jansson/libjansson.a"
else
  JANSSON_LIBS="-ljansson"
fi

# ======================
# Threads
# ======================
AC_CHECK_LIB([pthread], [pthread_create], PTHREAD_LIBS="-lpthread",
  AC_CHECK_LIB([pthreadGC2], [pthread_create], PTHREAD_LIBS="-lpthreadGC2",
    AC_CHECK_LIB([pthreadGC1], [pthread_create], PTHREAD_LIBS="-lpthreadGC1",
      AC_CHECK_LIB([pthreadGC], [pthread_create], PTHREAD_LIBS="-lpthreadGC"
))))

AC_SUBST(JANSSON_LIBS)
AC_SUBST(PTHREAD_FLAGS)
AC_SUBST(PTHREAD_LIBS)
AC_SUBST(WS2_LIBS)

# ======================
# Output
# ======================
AC_CONFIG_FILES([
  Makefile
  compat/Makefile
  compat/jansson/Makefile
])

AC_OUTPUT
