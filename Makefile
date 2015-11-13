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
	PacketFactory.java \
	PacketCheckpoint.java \
	PendingTransfer.java \
	ThreadedRunnable.java \
	Debugger.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
