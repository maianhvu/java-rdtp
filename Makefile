JFLAGS =
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	FileSender.java \
	FileReceiver.java \
	PacketService.java \
	PacketData.java \
	PacketSender.java \
	ThreadedRunnable.java \
	PendingTransfer.java \
	Debugger.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
