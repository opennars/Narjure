from time import time
from timeout import TimeoutError, none_val
import socket
import asyncore
from Queue import Queue
import logging

def createLogger(name, filepath='./log/'):
    # REF: https://docs.python.org/2/howto/logging-cookbook.html
    logger = logging.getLogger(name)
    logger.setLevel(logging.DEBUG)
    fh = logging.FileHandler(filepath+name+'.log')
    fh.setLevel(logging.DEBUG)
    ch = logging.StreamHandler()
    ch.setLevel(logging.INFO)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    fh.setFormatter(formatter)
    ch.setFormatter(formatter)
    logger.addHandler(fh)
    logger.addHandler(ch)
    return logger

#from Queue import *
#from multiprocessing import Pool

class NARSocket():
    END = '\n'
    E = -len(END)
    def __init__(self, host = 'localhost', port = 8080, name='narsocket', logpath='./log/'):
        self.SOCKET = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.SOCKET.connect((host, port))
        self.logger = createLogger(name, logpath)

    def write(self, msg):
        # Buffer the text
        write_buffer, msg = str(msg), str(msg)
        if len(msg) >= abs(self.E) and msg[self.E:] != self.END:
            write_buffer += '\n'

        # Begin writing
        while len(write_buffer) > 0:
            sent = self.SOCKET.send(write_buffer)
            self.logger.debug("Wrote :"+write_buffer[:sent])
            if sent == 0:
                self.SOCKET.close()
                raise RuntimeError("socket connection broken")
            write_buffer = write_buffer[sent:]
        self.logger.info("Wrote: "+msg)

    def read(self, timeout = None):
        read_buffer = ''
        start = time()
        stop = start + none_val(timeout)                                    # stop is set to start + timeout, timeout defaults to 0 if None
        while stop <= start or time() <= stop:                              # Runs forever if there is a zero or negative stop, or runs until time() is passed stop time
            timeout = stop - time()
            print("read here ", timeout)
            if timeout > 0:
                self.SOCKET.settimeout(timeout)
            else:
                self.SOCKET.settimeout(None)
            try:
                read = self.SOCKET.recv(1)
                self.SOCKET.settimeout(None)
            except socket.timeout:
                raise TimeoutError("Read timed out with buffer: " + read_buffer)
            self.logger.debug("Received: "+read)
            #print(read_buffer)
            if len(read) == 0:
                self.SOCKET.close()
                raise RuntimeError("socket connection broken")
            read_buffer += read
            if len(read_buffer) >= abs(self.E):
                if read_buffer[self.E:] == self.END:
                    self.logger.info("Received: "+read_buffer)
                    return read_buffer[:self.E]
        raise TimeoutError

    def close(self):
        print("Closing...")
        self.SOCKET.close()

class NARSocketA(asyncore.dispatcher):
    '''REF:https://docs.python.org/2/howto/sockets.html#socket-howto
       REF:https://docs.python.org/2/library/asyncore.html
    '''
    END = '\n'
    E   = -len(END)
    def __init__(self, host, port, callback = lambda x, y: True):
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.conn = (host, port)
        self.connect(self.conn)
        self.read_log, self.write_log = Queue(), Queue()
        self.write_buffer = ''
        self.read_buffer = ''
        self.callback = callback
        self.cb_memory = None

    def handle_connect(self):
        print "New Connection!"

    def handle_close(self):
        self.close()

    def readable(self):
        return True

    def handle_read(self):
        read = self.recv(1)
        if read == '':
            raise RuntimeError("socket connection broken")
        self.read_buffer += read
        if len(self.read_buffer) >= abs(self.E):
            if self.read_buffer[self.E:] == self.END:
                out = self.read_buffer[:self.E]
                self.read_buffer = ''
                cont = self.read_callback(out)
                if not cont:
                    self.cb_memory = None
                    self.close()

    def writable(self):
        return (len(self.write_buffer) > 0)

    def handle_write(self):
        sent = self.send(self.write_buffer)
        if sent == 0:
            raise RuntimeError("socket connection broken")
        self.write_buffer = self.write_buffer[sent:]

    def buff(self, msg):
        if len(msg) == 0 or msg[self.E:] != self.END:
            msg += '\n'
        self.write_buffer += msg
        self.write_log.put(msg)

    def read_callback(self, msg):
        self.read_log.put(msg)
        if self.cb_memory == None:
            cont, self.cb_memory = self.callback(msg)
        else:
            cont, self.cb_memory = self.callback(msg, self.cb_memory)
        return cont

    def loop(self):
        print("Looping...")
        asyncore.loop()
        print("Loop end.")

    def reconnect(self):
        self.connect(self.conn)
