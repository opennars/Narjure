from distutils.core import setup

setup(name='OpenNARS',
      version='2.0',
      description='A TCP port into the OpenNARS architecture.',
      author='Ryan Peach',
      author_email='ryan.peach@outlook.com',
      url='https://github.com/opennars/opennars2',
      package_dir = {'nars': 'src/examples/python'}
      packages=['nars']
)
