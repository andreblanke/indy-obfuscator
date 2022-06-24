INCLUDES := -I/usr/lib/jvm/java-18-openjdk/include/ -I/usr/lib/jvm/java-18-openjdk/include/linux/
LD_FLAGS := -L/usr/lib/jvm/java-18-openjdk/lib/server/
LD_LIBS  := -ljvm

.PHONY: all
all:
	g++ $(INCLUDES) $(LD_FLAGS) FindClass.cpp $(LD_LIBS)
	chmod +x a.out
