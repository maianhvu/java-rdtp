JFLAGS =
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	FileSender.java \
	FileReceiver.java \
	ProducerRunnable.java \
	ConsumerRunnable.java \
	Factory.java \
	PacketService.java \
	PacketData.java \
	PacketSender.java \
	ThreadedRunnable.java \
	ReceiverRunnable.java \
	Checkpoint.java \
	PendingTransfer.java \
	Debugger.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
