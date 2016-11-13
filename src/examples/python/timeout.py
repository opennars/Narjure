from time import time, sleep
from functools import wraps
import socket
import unittest

class TimeoutError(socket.timeout):
    pass

none_val = lambda x: 0.0 if x is None else float(x)                         # Returns float(x), or 0 if x is None

def loop_timeout(timeout = 0):
    """ A timeout loop decorator.
        Loops until function returns non-None value, or timeout is reached, raising a TimeoutError.
        If sub-function has \'timeout\' as a kwarg, if this kwarg has value None, will update this with remaining time on each loop.
        If super timeout <= 0 or timeout is None, then this will loop forever, and all sub-timeouts will remain unchanged.
        Returns: Output
        Raises: TimeoutError
        Sub-Returns: Output, kwargs : Returns the output
                     Output, None   : Returns the output
                     None, kwargs   : Loops and updates kwargs, must include all args, including those from *args.
                     None,          : Loops using same parameters. """
    def timeout_decorator(some_function):
        @wraps(some_function)
        def timeout_wrapper(*args, **kwargs):
            start = time()
            stop = start + none_val(timeout)                                    # stop is set to start + timeout, timeout defaults to 0 if None
            while stop <= start or time() <= stop:                              # Runs forever if there is a zero or negative stop, or runs until time() is passed stop time
                print(timeout)
                if not (stop <= start) \
                   and 'timeout' in kwargs \
                   and kwargs['timeout'] is None:                               # If timeout exists, and timeout exists in kwargs, and timeout in kwargs hasn't already been set
                   kwargs['timeout'] = stop-time()                              # ... then pass it the remaining time
                if args:                                                        # If args are still being used
                    out, new_kwargs = some_function(*args, **kwargs)            # ... some_function uses both args and kwargs.
                else:                                                    # After some kwargs are returned, and/or args are not used.
                    out, new_kwargs = some_function(**kwargs)                   # ... Only use kwargs

                if new_kwargs is not None and 'timeout' not in new_kwargs \
                    and 'timeout' in kwargs:
                    new_kwargs['timeout'] = None
                #    out, new_kwargs = some_function()
                if new_kwargs:                                                  # If kwargs is returned
                    kwargs = new_kwargs                                         # ... Replace kwargs
                    args = None                                                 # ... Use kwargs exclusively.
                if out is not None:                                             # While loop ends on some out other than None
                    return out                                                  # ...
            raise TimeoutError()                                                # If no return, raise a TimeoutError
        return timeout_wrapper
    return timeout_decorator

# ---------------- Test Methods ---------------
@loop_timeout(3)
def _print_wait(n):
    #print("Count: {}".format(n))
    sleep(1)
    if n > 0:
        return None, {'n':n-1}
    else:
        return True, None

class TestTimeout(unittest.TestCase):
    def test_timeout1(self):
        """ Tests timeout with exact steps."""
        self.assertTrue(_print_wait(n=2)) # Should return no error

    def test_timeout2(self):
        """ Tests timeout with greater than allowed number of steps."""
        with self.assertRaises(TimeoutError):
            _print_wait(n=3)

    def test_timeout3(self):
        """ Tests timeout with normal args rather than kwargs."""
        self.assertTrue(_print_wait(2))

if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(TestTimeout)
    unittest.TextTestRunner(verbosity=2).run(suite)
