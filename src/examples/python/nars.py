from narsocket import *
from timeout import TimeoutError, none_val
from uuid import uuid4 as uuid
import asyncore
from time import *
from Queue import *
from collections import *

# Constants
IN   = ":>:"
OUT  = ":<:"
CONFIRM = 'confirm'
INVALID = 'invalid'
RESERVED = set(['new-op','input','valid','concepts','concept','help','reset','quit','answer','say'])

def istrue(msg):
    return msg in ['True', 'true', 't', 'T', '1', '1.' '1.0']

class NARSOp:
    def __init__(self, id0, opname, *args):
        if args == None: args = []
        self.id, self.op, self.args, self.t = id0, opname, args, time()
    def inrep(self):
        return IN.join(iter(self))
    def outrep(self):
        return OUT.join(iter(self))
    def __str__(self):
        return self.outrep()
    def __iter__(self):
        return iter([self.id, self.op] + list(self.args))
    def __len__(self):
        return 2 + len(self.args)

class NARS(NARSocket):
    def error(self, id0, msg):
        self.write(NARSOp(id0,INVALID,msg))

    def confirm(self, id0, *args):
        self.write(NARSOp(id0,CONFIRM,*args))

    #def process_input(self, data, timeout = None):
    #    data = data.split(IN)
    #    if len(data) >= 2:
    #        out = NARSOp(*data)
    #        if out.op in self.ops:
    #            {'ask': self.ask,
    #             'input': self.input_narsese,
    #             'valid': self.valid_narsese,
    #             'concept': self.concept,
    #             'help': self.help,
    #             'reset': self.reset,
    #             'quit': self.quit}[out.op](out, timeout)
    #        else:
    #
    #    self.error(-1, "Improper Input Format.")

    def wait(self, key, timeout = None):
        start = time()
        stop = start + none_val(timeout)                                    # stop is set to start + timeout, timeout defaults to 0 if None
        while stop <= start or time() <= stop:                              # Runs forever if there is a zero or negative stop, or runs until time() is passed stop time
            timeout = stop - time()
            self.logger.debug("Waiting...")
            read = self.read(timeout=timeout).split(IN)
            self.logger.info("Wait Received: {}".format(read))
            data = NARSOp(*read)
            if key(data):
                print("wait here", timeout)
                return data.args
        raise TimeoutError

    def wait_conf(self, id0, timeout = None):
        start = time()
        stop = start + none_val(timeout)                                    # stop is set to start + timeout, timeout defaults to 0 if None
        while stop <= start or time() <= stop:                              # Runs forever if there is a zero or negative stop, or runs until time() is passed stop time
            timeout = stop - time()
            self.logger.debug("Waiting...")
            read = self.read(timeout).split(IN)
            self.logger.info("Wait Received: {}".format(read))
            data = NARSOp(*read)
            if data.id == id0:
                if data.op == CONFIRM:
                    return True
                elif data.op == INVALID:
                    return False
        raise TimeoutError

    def _input_narsese(self, op, *msgs, **kwargs):
        # Unpack inputs
        if 'timeout' in kwargs: timeout = kwargs['timeout']
        else: timeout = None
        if 'id0' in kwargs: id0 = kwargs['id0']
        else: id0 = str(uuid())

        # Begin write
        self.write(NARSOp(id0,op,*msgs))
        self.logger.debug("Done Writing")
        ret = self.wait(lambda d: d.id == id0 and d.op == 'valid', timeout)
        out = [istrue(x) for x in ret]
        self.logger.debug("Done Waiting: "+str(out))
        failed = [msg for tf, msg in zip(out,msgs) if not tf]
        self.logger.debug("Failures: "+str(failed))
        if len(out) == 1:
            return out[0]
        else:
            return out, failed

    def input_narsese(self, *msgs, **kwargs):
        return self._input_narsese('input',*msgs,**kwargs)
    def valid_narsese(self, *msgs, **kwargs):
        return self._input_narsese('valid',*msgs,**kwargs)

    def ask(self, msg, timeout = None):
        if '>?' not in msg:
            raise Exception("Message must end in a question mark.")
        id0 = str(uuid())
        valid = self._input_narsese('ask', msg, timeout=timeout, id0=id0)
        if not valid:
            raise Exception("Message invalid.")
        self.logger.debug("Confirmed. Awaiting reply.")
        try:
            return self.wait(lambda d: d.op == 'answer' and d.id == id0, timeout)
        except TimeoutError as e:
            self.logger.error("Timeout.")
            raise TimeoutError

    def concept(self, *args, **kwargs):
        # Unpack inputs
        if 'timeout' in kwargs: timeout = kwargs['timeout']
        else: timeout = None
        if 'id0' in kwargs: id0 = kwargs['id0']
        else: id0 = str(uuid())
        if not args:
            opname = 'concept'
            self.write(NARSOp(id0, opname))
        else:
            opname = 'concept'
            self.write(NARSOp(id0, opname, *args))
        out = self.wait(lambda d: d.id == id0 and d.op == 'concept', timeout)
        test = [i != INVALID for i in out]
        return [i if good else None for i, good in zip(out, test)]

    def parse(self, msg, timeout = None):
        id0, opname = str(uuid()), 'parse'
        self.write(NARSOp(id0, opname, msg))
        return self.wait(lambda d: d.id == id0 and d.op == opname, timeout)

    def help(self, timeout = None):
        id0, opname = str(uuid()), 'help'
        self.write(NARSOp(id0, opname))
        return self.wait(lambda d: d.id == id0 and d.op == opname, timeout)

    def reset(self, timeout = None):
        id0, opname = str(uuid()), 'reset'
        self.write(NARSOp(id0, opname))
        return self.wait_conf(id0, timeout)

    def quit(self, timeout = None):
        id0, opname = str(uuid()), 'quit'
        self.write(NARSOp(id0, opname))
        if self.wait_conf(id0, timeout):
            self.close()
        else:
            raise Exception("Could not quit.")

from functools import partial
class NARSHost(NARSocketA):
    def __init__(self, host, port, callbacks, each_read = lambda x, y: True, rules = []):
        super(NARSocketA, self).__init__(host, port, each_read)
        for k, f in callbacks.iteritems(): # Initialize callbacks
            conf = self.new_op(k, f)
            if not conf: raise Exception("Invalid key: {}".format(k))
        conf = self.input_narsese(*rules)
        bad = [r for r, good in zip(rules, conf) if not good]
        if len(bad) > 0: raise Exception("Invalid narsese: {}".format(bad))

    def input_narsese(self, *args):
        """ Assumes the narsese is good. """
        pass

    def new_op(self, opname, f):
        id0 = uuid4()
        self.ops[opname] = f
        self.buff(NARSOp(id0,'new-op',opname).outrep())
        return istrue(self.wait(id0, 'new-op')[0])

if __name__=="__main__":
    import test
