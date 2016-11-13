from narsocket import *
from nars import *
from timeout import TimeoutError
from uuid import uuid4 as uuid
import argparse
from Queue import *
import unittest
from pprint import pprint

QUIT = []
IN   = ":<:"
OUT  = ":>:"

parser = argparse.ArgumentParser(description='Process Server Parameters')
parser.add_argument('-a','--address', metavar='ADDRESS', type=str, default='localhost',
                   help='Address to connect to.')
parser.add_argument('-p','--port', metavar='PORT', type=int, default=8080,
                   help='Port for the connection.')
parser.add_argument('-b','--buffsz', metavar='BUFFER (bits)', type=int, default=512,
                   help='Buffer size.')
args = parser.parse_args()
address = args.address
port    = args.port
buffsz  = args.buffsz

def queue_to_list(Q):
    out = []
    while not Q.empty():
        out.append(Q.get())
    return out

def test_NARSocket():
    client = NARSocket(address, port)
    out = ["{}:<:input:<:<a-->b>.".format(uuid()),
           "{}:<:input:<:<b-->c>.".format(uuid()),
           "{}:<:ask:<:<a-->c>?".format(uuid())]
    for t in out:
        client.write(t)
        print(client.read())
    print(client.read())
    client.close()

def test_NARSocketA():
    def callback(msg, memory = {'count': 0}):
        print("Reading: {}".format(msg))
        memory['count'] += 1
        if memory['count'] >= 4:
            return False, memory
        else:
            return True, memory

    client = NARSocketA(address, port, callback)
    out = ["{}:<:input:<:<a-->b>.".format(uuid()),
           "{}:<:input:<:<b-->d>.".format(uuid()),
           "{}:<:ask:<:<a-->d>?".format(uuid())]
    for t in out:
        client.buff(t)
    client.loop()
    pprint(queue_to_list(client.read_log))
    client.close()

class TestOnlineNARS():
    def __init__(self):
        self.setUp()

    def setUp(self):
        self.client = NARS(address, port)

    def _test_input_narsese(self, op):
        ok, bad = op("<a-->b>.", "<a->b>.")
        print(ok, bad)
        assert not all(ok), "ok contained false. "+str(ok)
        assert bad == ["<a->b>."], "bad did not equal proper value. {} != {}".format(bad, ["<a->b>."])
        assert ok[0], "ok[0] is not True. {}".format(ok)
        in1 = op("<a<-b>.")
        assert not in1, "Did not detect incorrect narsese as expected. "+str(in1)
        in2 = op("<a-->b>.")
        assert in2, "Did not detect correct narsese as expected."+str(in2)
    def test_input_narsese(self): self._test_input_narsese(self.client.input_narsese)
    def test_valid_narsese(self): self._test_input_narsese(self.client.valid_narsese)

    def test_ask(self):
        self.client.input_narsese("<a-->b>.", "<b-->c>.")
        assert(self.client.ask("<a-->c>?"))
        try:
            self.client.ask("<a-->d>?", 1)
            raise Exception("Expected TimeoutError")
        except TimeoutError:
            pass

    def test_op(self):
        self.client._input_narsese('new-op','plus')
        self.client.input_narsese("<(*, 1, 2, ?out) --> plus>!", id0="1")
        heard = self.client.wait(lambda: True)
        print("op", heard)
        assert(heard[0] == "^plus")
        self.confirm()

    def test_concept(self):
        print(self.client.concept())
        print(self.client.concept("<a-->b>."))
    def test_parse(self):
        print(self.client.parse("<a-->b>."))
    def test_help(self):
        print(self.client.help())
    def test_reset(self):
        assert(self.client.reset())
    def test_quit(self):
        assert(self.client.quit())

if __name__=="__main__":
    def test(f, *args):
        print("\n----------------")
        print("Testing {}".format(f.__name__))
        #try:
        if len(args)>0: out = f(*args)
        else: out = f()
        print("----------------")
        print("{}: Passed, {}".format(f.__name__, out))
        print("----------------")
        #except Exception as e:
        #    print("----------------")
        #    print("{}: Failed, {}".format(f.__name__, e))
        #    print("----------------")

    #test(test_NARSocket)
    #test(test_NARSocketA)

    OLNARS = TestOnlineNARS()
    test(OLNARS.test_input_narsese)
    test(OLNARS.test_valid_narsese)
    test(OLNARS.test_ask)
    test(OLNARS.test_op)
    #test(OLNARS.test_concept)
    #test(OLNARS.test_parse)
    #test(OLNARS.test_help)
    #test(OLNARS.test_reset)
    #test(OLNARS.test_quit)
# Loop
#while True:
#    data = raw_input()
#    if data == 'quit':
#        print("Quitting...")
#        break
#    if data == 'read' or data == '':
#        print("Waiting...")
#        print("Read: " + client.force_read())
#    else:
#        print("Sending: ", data)
#        client.buff(data)
