JFLAGS =
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	FileSender.java \
	FileReceiver.java \
	MetaPacket.java \
	PacketSender.java \
	ThreadedRunnable.java \
	Debugger.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
