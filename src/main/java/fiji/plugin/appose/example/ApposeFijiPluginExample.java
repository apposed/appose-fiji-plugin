package fiji.plugin.appose.example;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

public class ApposeFijiPluginExample implements PlugIn
{

	@Override
	public void run( final String arg )
	{
		final ImagePlus imp = WindowManager.getCurrentImage();
		try
		{
			process( imp );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
	}

	public static < T extends RealType< T > & NativeType< T > > void process( final ImagePlus imp ) throws IOException
	{
		// Print os and arch info
		System.out.println( "This machine os and arch:" );
		System.out.println( "  " + System.getProperty( "os.name" ) );
		System.out.println( "  " + System.getProperty( "os.arch" ) );
		System.out.println();

		// The mamba environment spec.
		final String cellposeEnv = mambaEnv();
		System.out.println( "The mamba environment specs:" );
		System.out.println( indent( cellposeEnv ) );
		System.out.println();

		// Get the script
		final String script = getScript();
		System.out.println( "The analysis script" );
		System.out.println( indent( script ) );
		System.out.println();

		// Copy the input to a shared memory image.
		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > img = rawWraps( imp );
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "image", NDArrays.asNDArray( img ) );

		// Create or retrieve the environment.
		final Environment env = Appose
				.mamba()
				.content( cellposeEnv )
				.logDebug()
				.build();

		try (Service python = env.python())
		{
			final Task task = python.task( script, inputs );
			System.out.println( "Starting task" );
			final long start = System.currentTimeMillis();
			task.start();
			task.waitFor();

			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );

			// Benchmark.
			final long end = System.currentTimeMillis();
			System.out.println( "Task finished in " + ( end - start ) / 1000. + " s" );

			// Unwrap output.
			final NDArray maskArr = ( NDArray ) task.outputs.get( "rotated" );
			final Img< T > output = new ShmImg<>( maskArr );
			ImageJFunctions.show( output );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	private static String getScript()
	{
		return "from skimage.io import imread, imsave\n"
				+ "from skimage.transform import rotate\n"
				+ "import appose\n"
				+ "\n"
				+ "img = image.ndarray()\n"
				+ "\n"
				+ "# Rotate the image by 90 degrees (counter-clockwise)\n"
				+ "rotated_image = rotate(image, angle=90, resize=True)\n"
				+ "\n"
				+ "# Output back to Fiji\n"
				+ "shared = appose.NDArray(str(rotated_image.dtype), rotated_image.shape)\n"
				+ "shared.ndarray()[:] = rotated_image[:]\n"
				+ "task.outputs['rotated'] = shared\n" + "";
	}

	public static String mambaEnv()
	{
		return "name: image-rotation\n"
				+ "channels:\n"
				+ "  - conda-forge\n"
				+ "dependencies:\n"
				+ "  - python=3.10\n"
				+ "  - pip\n"
				+ "  - scikit-image\n"
				+ "  - pip:\n"
				+ "    - numpy\n";
	}

	private static String indent( final String script )
	{
		final String[] split = script.split( "\n" );
		String out = "";
		for ( final String string : split )
			out += "    " + string + "\n";
		return out;
	}

	@SuppressWarnings( "rawtypes" )
	public static final ImgPlus rawWraps( final ImagePlus imp )
	{
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		return raw;
	}

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		IJ.openImage( "http://imagej.net/images/blobs.gif" ).show();
		final ApposeFijiPluginExample plugin = new ApposeFijiPluginExample();
		plugin.run( "" );
	}
}
