AUTOMAKE_OPTIONS = subdir-objects

if WANT_JANSSON
JANSSON_INCLUDES= -I$(top_srcdir)/compat/jansson
else
JANSSON_INCLUDES=
endif

EXTRA_DIST			= example-cfg.json

SUBDIRS					= compat

bin_PROGRAMS		= sugarmaker

dist_man_MANS		= sugarmaker.1

sugarmaker_SOURCES	=	elist.h miner.h compat.h \
									cpu-miner.c util.c \
									sha2.c \
									yespower-1.0.1/sha256.c yespower-1.0.1/yespower-opt.c \
									YespowerSugar.c \
									YespowerIso.c \
									YespowerNull.c \
									YespowerUrx.c \
									YespowerLitb.c \
									YespowerIots.c \
									YespowerItc.c \
									yespower-1.0.1-power2b/sha256-p2b.c yespower-1.0.1-power2b/yespower-opt-p2b.c yespower-1.0.1-power2b/blake2b.c \
									YespowerMbc.c \
									YespowerYtn.c \
									YespowerAdvc.c \
									YespowerMwc.c

sugarmaker_LDFLAGS	= $(PTHREAD_FLAGS)
sugarmaker_LDADD		= @LIBCURL@ @JANSSON_LIBS@ @PTHREAD_LIBS@ @WS2_LIBS@
sugarmaker_CPPFLAGS	= $(PTHREAD_FLAGS) @CPPFLAGS@ $(JANSSON_INCLUDES)
