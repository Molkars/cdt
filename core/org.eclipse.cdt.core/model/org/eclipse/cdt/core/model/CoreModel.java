/*******************************************************************************
 * Copyright (c) 2000, 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *     Markus Schorn (Wind River Systems)
 *******************************************************************************/
package org.eclipse.cdt.core.model;


import org.eclipse.cdt.core.CCProjectNature;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.CProjectNature;
import org.eclipse.cdt.core.resources.IPathEntryStore;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionListener;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionManager;
import org.eclipse.cdt.core.settings.model.WriteAccessException;
import org.eclipse.cdt.internal.core.model.APathEntry;
import org.eclipse.cdt.internal.core.model.BatchOperation;
import org.eclipse.cdt.internal.core.model.CModel;
import org.eclipse.cdt.internal.core.model.CModelManager;
import org.eclipse.cdt.internal.core.model.ContainerEntry;
import org.eclipse.cdt.internal.core.model.IncludeEntry;
import org.eclipse.cdt.internal.core.model.IncludeFileEntry;
import org.eclipse.cdt.internal.core.model.LibraryEntry;
import org.eclipse.cdt.internal.core.model.MacroEntry;
import org.eclipse.cdt.internal.core.model.MacroFileEntry;
import org.eclipse.cdt.internal.core.model.OutputEntry;
import org.eclipse.cdt.internal.core.model.PathEntryManager;
import org.eclipse.cdt.internal.core.model.ProjectEntry;
import org.eclipse.cdt.internal.core.model.SourceEntry;
import org.eclipse.cdt.internal.core.settings.model.CLanguageSettingCache;
import org.eclipse.cdt.internal.core.settings.model.CProjectDescriptionManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

public class CoreModel {
	private static CoreModel cmodel = null;
	private static CModelManager manager = CModelManager.getDefault();
	private static PathEntryManager pathEntryManager = PathEntryManager.getDefault();
	private static CProjectDescriptionManager descriptionManager = CProjectDescriptionManager.getInstance();
	
//	private static String FILE_EXT_PATTERN = "*."; //$NON-NLS-1$
//	private static int FILE_EXT_PATTERN_LENGTH = FILE_EXT_PATTERN.length();

	public final static String CORE_MODEL_ID = CCorePlugin.PLUGIN_ID + ".coremodel"; //$NON-NLS-1$

	/**
	 * Creates an ICElement form and IPath. Returns null if not found.
	 */
	public ICElement create(IPath path) {
		return manager.create(path);
	}

	/**
	 * Creates a translation form and IPath. Returns null if not found.
	 */
	public ITranslationUnit createTranslationUnitFrom(ICProject cproject, IPath path) {
		return manager.createTranslationUnitFrom(cproject, path);
	}

	/**
	 * Creates an ICElement form and IFile. Returns null if not found.
	 */
	public ICElement create(IFile file) {
		return manager.create(file, null);
	}

	/**
	 * Creates an ICElement form and IFolder. Returns null if not found.
	 */
	public ICContainer create(IFolder folder) {
		return manager.create(folder, null);
	}

	/**
	 * Creates an ICElement form and IProject. Returns null if not found.
	 */
	public ICProject create(IProject project) {
		if (project == null) {
			return null;
		}
		CModel cModel = manager.getCModel();
		return cModel.getCProject(project);
	}

	/**
	 * Creates an ICElement form and IResource. Returns null if not found.
	 */
	public ICElement create(IResource resource) {
		return manager.create(resource, null);
	}

	/**
	 * Returns the C model.
	 * 
	 * @param root the given root
	 * @return the C model, or <code>null</code> if the root is null
	 */
	public static ICModel create(IWorkspaceRoot root) {
		if (root == null) {
			return null;
		}
		return manager.getCModel();
	}
	/**
	 * Returns the default ICModel.
	 */
	public ICModel getCModel() {
		return manager.getCModel();
	}

	/**
	 * Return true if IFile is a shared library, i.e. libxx.so
	 */
	public boolean isSharedLib(IFile file) {
		ICElement celement = create(file);
		if (celement instanceof IBinary) {
			return ((IBinary)celement).isSharedLib();
		}
		return false;
	}

	/**
	 * Return true if IFile is a an object(ELF), i.e. *.o
	 */
	public boolean isObject(IFile file) {
		ICElement celement = create(file);
		if (celement instanceof IBinary) {
			return ((IBinary)celement).isObject();
		}
		return false;
	}

	/**
	 * Return true if IFile is an ELF executable
	 */
	public boolean isExecutable(IFile file) {
		ICElement celement = create(file);
		if (celement instanceof IBinary) {
			return ((IBinary)celement).isExecutable();
		}
		return false;
	}

	/**
	 * Return true if IFile is an ELF.
	 */
	public boolean isBinary(IFile file) {
		ICElement celement = create(file);
		return (celement instanceof IBinary);
	}

	/**
	 * Return true if IFile is an Achive, *.a
	 */
	public boolean isArchive(IFile file) {
		ICElement celement = create(file);
		return(celement instanceof IArchive);
	}

	/**
	 * Return true if IFile is a possible TranslationUnit.
	 */
	public static boolean isTranslationUnit(IFile file) {
		if (file != null) {
			IProject p = file.getProject();
			if (hasCNature(p) || hasCCNature(p)) {
				return isValidTranslationUnitName(p, file.getFullPath().lastSegment());
			}
		}
		return false;
	}

	/**
	 * Return an array of the register contentTypes.
	 * @return String[] ids
	 */
	public static String[] getRegistedContentTypeIds() {
		return LanguageManager.getInstance().getRegisteredContentTypeIds();
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidTranslationUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			return CCorePlugin.CONTENT_TYPE_CHEADER.equals(id)
				|| CCorePlugin.CONTENT_TYPE_CXXHEADER.equals(id)
				|| CCorePlugin.CONTENT_TYPE_CSOURCE.equals(id)
				|| CCorePlugin.CONTENT_TYPE_CXXSOURCE.equals(id)
				|| CCorePlugin.CONTENT_TYPE_ASMSOURCE.equals(id)
				|| LanguageManager.getInstance().isContributedContentType(id);
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidHeaderUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CHEADER.equals(id)) {
				return true;
			} else if (CCorePlugin.CONTENT_TYPE_CXXHEADER.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidSourceUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CHEADER.equals(id)
					|| CCorePlugin.CONTENT_TYPE_CXXHEADER.equals(id))
				return false;

			return CCorePlugin.CONTENT_TYPE_CSOURCE.equals(id)
				|| CCorePlugin.CONTENT_TYPE_CXXSOURCE.equals(id)
				|| CCorePlugin.CONTENT_TYPE_ASMSOURCE.equals(id)
				|| LanguageManager.getInstance().isContributedContentType(id);
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidCSourceUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CSOURCE.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidCXXSourceUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CXXSOURCE.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidASMSourceUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_ASMSOURCE.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidCXXHeaderUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CXXHEADER.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if name is a valid name for a translation unit.
	 */
	public static boolean isValidCHeaderUnitName(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null) {
			String id = contentType.getId();
			if (CCorePlugin.CONTENT_TYPE_CHEADER.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 *  Return the registed content type id, for example:
	 *  <ul>
	 *  <li>CONTENT_TYPE_CHEADER
	 *  <li>CONTENT_TYPE_CXXHEADER
	 *  <li>CONTENT_TYPE_CSOURCE
	 *  <li>CONTENT_TYPE_CXXSOURCE
	 *  <li>CONTENT_TYPE_ASMSOURCE
	 *  </ul>
	 *  or null is return if no id match the list
	 * @param file
	 * @return the know id or null
	 */
	public static String getRegistedContentTypeId(IProject project, String name) {
		IContentType contentType = CCorePlugin.getContentType(project, name);
		if (contentType != null && LanguageManager.getInstance().getLanguage(contentType) != null) {
			return contentType.getId();
		}
		return null;
	}


	/**
	 * Return true if project has C nature.
	 */
	public static boolean hasCNature(IProject project) {
		boolean ok = false;
		try {
			ok = (project.isOpen() && project.hasNature(CProjectNature.C_NATURE_ID));
		} catch (CoreException e) {
			//throws exception if the project is not open.
			//System.out.println (e);
			//e.printStackTrace();
		}
		return ok;
	}

	/**
	 * Return true if project has C++ nature.
	 */
	public static boolean hasCCNature(IProject project) {
		boolean ok = false;
		try {
			ok = (project.isOpen() && project.hasNature(CCProjectNature.CC_NATURE_ID));
		} catch (CoreException e) {
			//throws exception if the project is not open.
			//System.out.println (e);
			//e.printStackTrace();
		}
		return ok;
	}

	/**
	 * Creates and returns a new non-exported entry of kind <code>CDT_PROJECT</code>
	 * for the project identified by the given absolute path.
	 * <p>
	 * A project entry is used to denote a prerequisite project. The
	 * exported IPathEntry[] entries of the project will be contributed.
	 * <p>
	 * The prerequisite project is referred to using an absolute path relative
	 * to the workspace root.
	 * <p>
	 * The resulting entry is not exported to dependent projects. This method
	 * is equivalent to <code>newProjectEntry(path,false)</code>.
	 * <p>
	 * 
	 * @param projectPath
	 *            the workspace-relative path of the project
	 * @return a new project entry
	 * 
	 * @see CoreModel#newProjectEntry(IPath, boolean)
	 */
	public static IProjectEntry newProjectEntry(IPath projectPath) {
		return newProjectEntry(projectPath, false);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_PROJECT</code> for
	 * the project identified by the given workspace-relative path.
	 * <p>
	 * A project entry is used to denote a prerequisite project. All the
	 * IPathEntries of the project will be contributed as a whole. The
	 * prerequisite project is referred to using an absolute path relative to
	 * the workspace root.
	 * <p>
	 * 
	 * @param projectPath
	 *            the absolute workspace-relative path of the prerequisite project
	 * @param isExported
	 *            indicates whether this entry is contributed to dependent
	 *            projects
	 * @return a new project entry
	 */
	public static IProjectEntry newProjectEntry(IPath projectPath, boolean isExported) {
		return new ProjectEntry(projectPath, isExported);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_CONTAINER</code> for
	 * the given path. The path of the container will be used during resolution
	 * so as to map this container entry to a set of other entries the
	 * container is acting for.
	 * <p>
	 * The resulting entry is not exported to dependent projects. This method
	 * is equivalent to <code>newContainerEntry(path,false)</code>.
	 * <p>
	 * 
	 * @param containerPath
	 *            the id of the container
	 * @return a new container entry
	 *  
	 */
	public static IContainerEntry newContainerEntry(IPath id) {
		return newContainerEntry(id, false);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_CONTAINER</code> for
	 * the given path. The path of the container will be used during resolution
	 * so as to map this container entry to a set of other entries the
	 * container is acting for.
	 * <p>
	 * The resulting entry is not exported to dependent projects. This method
	 * is equivalent to <code>newContainerEntry(path,false)</code>.
	 * <p>
	 */
	public static IContainerEntry newContainerEntry(IPath id, boolean isExported) {
		return new ContainerEntry(id, isExported);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_LIBRARY</code>
	 * for the archive or folder identified by the given absolute path.
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param baseRef
	 *            the base reference path to find the library
	 * @param libraryPath
	 *            the library name.
	 * @return a new library entry
	 *  
	 */
	public static ILibraryEntry newLibraryRefEntry(IPath resourcePath, IPath baseRef, IPath libraryPath) {
		return new LibraryEntry(resourcePath, null, baseRef, libraryPath, null, null, null, false);
	}


	/**
	 * Creates and returns a new entry of kind <code>CDT_LIBRARY</code>
	 * for the archive or folder identified by the given absolute path.
	 * 
	 * Note that this operation does not attempt to validate or access the
	 * resources at the given paths.
	 * <p>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the library
	 * @param libraryPath
	 *            the path of the library
	 * @param sourceAttachmentPath
	 *            the project-relative path of the corresponding source archive or
	 *            folder, or <code>null</code> if none.
	 * @param sourceAttachmentRootPath
	 *            the location of the root within the source archive or folder
	 *            or <code>null</code>.
	 * @param sourceAttachmentPrefixMapping
	 *            prefix mapping or <code>null</code>.
	 * @param isExported
	 *           whether the entry is exported
	 * @return a new library entry
	 *  
	 */
	public static ILibraryEntry newLibraryEntry(IPath resourcePath, IPath basePath, IPath libraryPath, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath,
			IPath sourceAttachmentPrefixMapping, boolean isExported) {
		return new LibraryEntry(resourcePath, basePath, null, libraryPath, sourceAttachmentPath, sourceAttachmentRootPath, sourceAttachmentPrefixMapping, isExported);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_OUTPUT</code> for
	 * the project's output folder
	 * <p>
	 * 
	 * @param outputPath
	 *            the project-relative path of a folder
	 * @return a new source entry with not exclusion patterns
	 *  
	 */
	public static IOutputEntry newOutputEntry(IPath outputPath) {
		return newOutputEntry(outputPath, APathEntry.NO_EXCLUSION_PATTERNS);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_OUPUT</code> for
	 * the project
	 * 
	 * @param outputPath
	 *            the project-relative path of a folder
	 * @param exclusionPatterns
	 *            the possibly empty list of exclusion patterns represented as
	 *            relative paths
	 * @return a new source entry with the given exclusion patterns
	 */
	public static IOutputEntry newOutputEntry(IPath outputPath, IPath[] exclusionPatterns) {
		return new OutputEntry(outputPath, exclusionPatterns, false);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_SOURCE</code> for
	 * the project's source folder identified by the given absolute
	 * workspace-relative path.
	 * <p>
	 * The source folder is referred to using an absolute path relative to the
	 * workspace root, e.g. <code>/Project/src</code>. A project's source
	 * folders are located with that project. That is, a source entry
	 * specifying the path <code>/P1/src</code> is only usable for project
	 * <code>P1</code>.
	 * </p>
	 * </p>
	 * <p>
	 * Note that all sources/binaries inside a project are contributed as a
	 * whole through a project entry (see <code>newProjectEntry</code>).
	 * Particular source entries cannot be selectively exported.
	 * </p>
	 * 
	 * @param sourcePath
	 *            the project-relative path of a source folder
	 * @return a new source entry with not exclusion patterns
	 *  
	 */
	public static ISourceEntry newSourceEntry(IPath sourcePath) {
		return newSourceEntry(sourcePath, APathEntry.NO_EXCLUSION_PATTERNS);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_SOURCE</code> for
	 * the project's source folder identified by the given absolute
	 * workspace-relative path but excluding all source files with paths
	 * matching any of the given patterns. This specifies that all package
	 * fragments within the root will have children of type <code>ICompilationUnit</code>.
	 * <p>
	 * The source folder is referred to using an absolute path relative to the
	 * workspace root, e.g. <code>/Project/src</code>. A project's source
	 * folders are located with that project. That is, a source entry
	 * specifying the path <code>/P1/src</code> is only usable for project
	 * <code>P1</code>.
	 * </p>
	 * 
	 * @param sourcePath
	 *            the absolute project-relative path of a source folder
	 * @param exclusionPatterns
	 *            the possibly empty list of exclusion patterns represented as
	 *            relative paths
	 * @return a new source entry with the given exclusion patterns
	 */
	public static ISourceEntry newSourceEntry(IPath sourcePath, IPath[] exclusionPatterns) {
		return new SourceEntry(sourcePath, exclusionPatterns);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_INCLUDE</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the includePath
	 * @param includePath
	 *            the absolute path of the include
	 * @return IIncludeEntry
	 */
	public static IIncludeEntry newIncludeEntry(IPath resourcePath, IPath basePath, IPath includePath) {
		return newIncludeEntry(resourcePath, basePath, includePath, true);
	}

	/**
	 * * Creates and returns a new entry of kind <code>CDT_INCLUDE</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the includePath
	 * @param includePath
	 *            the absolute path of the include
	 * @param isSystemInclude
	 *            whether this include path should be consider a system include path
	 * @return IIncludeEntry
	 */
	public static IIncludeEntry newIncludeEntry(IPath resourcePath, IPath basePath, IPath includePath, boolean isSystemInclude) {
		return newIncludeEntry(resourcePath, basePath, includePath, isSystemInclude, APathEntry.NO_EXCLUSION_PATTERNS);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_INCLUDE</code>
	 * 
	 * @param resoourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the includePath
	 * @param includePath
	 *            the absolute path of the include
	 * @param isSystemInclude
	 *            wheter this include path should be consider the system
	 *            include path
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @return IIincludeEntry
	 */
	public static IIncludeEntry newIncludeEntry(IPath resourcePath, IPath basePath, IPath includePath, boolean isSystemInclude, IPath[] exclusionPatterns) {
		return newIncludeEntry(resourcePath, basePath, includePath, isSystemInclude, exclusionPatterns, false);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_INCLUDE</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the include
	 * @param includePath
	 *            the path of the include
	 * @param isSystemInclude
	 *            wheter this include path should be consider the system
	 *            include path
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @param isExported
	 *            if the entry ix exported to reference projects
	 * @return IIincludeEntry
	 */
	public static IIncludeEntry newIncludeEntry(IPath resourcePath, IPath basePath, IPath includePath, boolean isSystemInclude,
			 IPath[] exclusionPatterns, boolean isExported) {
		return new IncludeEntry(resourcePath, basePath, null, includePath, isSystemInclude, exclusionPatterns, isExported);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_INCLUDE</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param baseRef
	 *            the base reference path of the include
	 * @param includePath
	 *            the path of the include
	 * @return IIincludeEntry
	 */
	public static IIncludeEntry newIncludeRefEntry(IPath resourcePath, IPath baseRef, IPath includePath) {
		return new IncludeEntry(resourcePath, null, baseRef, includePath, true, null, false);
	}

	/**
	 * Creates a new entry of kind <code>CDT_INCLUDE_FILE</code>
	 * 
	 * @param resourcePath
	 * @param includeFile
	 * @return
	 */
	public static IIncludeFileEntry newIncludeFileEntry(IPath resourcePath, IPath includeFile) {
		return newIncludeFileEntry(resourcePath, null, null, includeFile, null, false);
	}

	/**
	 * Creates and returns a new entry of kind <code>CDT_INCLUDE_FILE</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param basePath
	 *            the base path of the include
	 * @param includeFilePath
	 *            the path of the include
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @param isExported
	 *            if the entry ix exported to reference projects
	 * @return IIincludeEntry
	 */
	public static IIncludeFileEntry newIncludeFileEntry(IPath resourcePath, IPath baseRef, IPath basePath, IPath includeFilePath,
			 IPath[] exclusionPatterns, boolean isExported) {
		return new IncludeFileEntry(resourcePath, basePath, baseRef, includeFilePath, exclusionPatterns, isExported);
	}

	/**
	 * Creates and returns an entry kind <code>CDT_MACRO</code>
	 * 
	 * @param path
	 *            the affected project-relative resource path
	 * @param macroName
	 *            the name of the macro
	 * @param macroValue
	 *            the value of the macro
	 * @return IMacroEntry
	 */
	public static IMacroEntry newMacroEntry(IPath resourcePath, String macroName, String macroValue) {
		return newMacroEntry(resourcePath, macroName, macroValue, APathEntry.NO_EXCLUSION_PATTERNS);
	}

	/**
	 * Creates and returns an entry kind <code>CDT_MACRO</code>
	 * 
	 * @param resourcePath
	 *            the affected project-relative resource path
	 * @param macroName
	 *            the name of the macro
	 * @param macroValue
	 *            the value of the macro
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @return
	 */
	public static IMacroEntry newMacroEntry(IPath resourcePath, String macroName, String macroValue, IPath[] exclusionPatterns) {
		return newMacroEntry(resourcePath, macroName, macroValue, exclusionPatterns, false);
	}

	/**
	 * Creates and returns an entry kind <code>CDT_MACRO</code>
	 * 
	 * @param resourcePath
	 *            the affected workspace-relative resource path
	 * @param macroName
	 *            the name of the macro
	 * @param macroValue
	 *            the value of the macro
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @return
	 */
	public static IMacroEntry newMacroEntry(IPath resourcePath, String macroName, String macroValue, IPath[] exclusionPatterns, boolean isExported) {
		return new MacroEntry(resourcePath, null, macroName, macroValue, exclusionPatterns, isExported);
	}

	/**
	 * Creates and returns an entry kind <code>CDT_MACRO</code>
	 * 
	 * @param resourcePath
	 *            the affected workspace-relative resource path
	 * @param baseRef
	 *        the base reference path
	 * @param macroName
	 *            the name of the macro
	 * @return IMacroEntry
	 */
	public static IMacroEntry newMacroRefEntry(IPath resourcePath, IPath baseRef, String macroName) {
		return new MacroEntry(resourcePath, baseRef, macroName, null, APathEntry.NO_EXCLUSION_PATTERNS, false);
	}

	/**
	 * Creates an entry kind <code>CDT_MACRO_FILE</code>
	 * 
	 * @param resourcePath
	 * @param macroFile
	 * @return
	 */
	public static IMacroFileEntry newMacroFileEntry(IPath resourcePath, IPath macroFile) {
		return newMacroFileEntry(resourcePath, null, null, macroFile, null, false);
	}

	/**
	 * Creates and returns an entry kind <code>CDT_MACRO_FILE</code>
	 * 
	 * @param resourcePath
	 *            the affected workspace-relative resource path
	 * @param basePath
	 *            the base path
	 * @param macroFilePath
	 *            the file path where the macros are define
	 * @param exclusionPatterns
	 *            exclusion patterns in the resource if a container
	 * @return
	 */
	public static IMacroFileEntry newMacroFileEntry(IPath resourcePath, IPath basePath, IPath baseRef, IPath macroFilePath, IPath[] exclusionPatterns, boolean isExported) {
		return new MacroFileEntry(resourcePath, basePath, baseRef, macroFilePath, exclusionPatterns, isExported);
	}


	/**
	 * Answers the project specific value for a given container. In case this
	 * container path could not be resolved, then will answer <code>null</code>.
	 * Both the container path and the project context are supposed to be
	 * non-null.
	 * <p>
	 * The containerPath is a formed by a first ID segment followed with extra
	 * segments, which can be used as additional hints for resolution. If no
	 * container was ever recorded for this container path onto this project
	 * (using <code>setPathEntryContainer</code>, then a <code>PathEntryContainerInitializer</code>
	 * will be activated if any was registered for this container ID onto the
	 * extension point "org.eclipse.cdt.core.PathEntryContainerInitializer".
	 * <p>
	 * PathEntry container values are persisted locally to the workspace, but
	 * are not preserved from a session to another. It is thus highly
	 * recommended to register a <code>PathEntryContainerInitializer</code>
	 * for each referenced container (through the extension point
	 * "org.eclipse.cdt.core.PathEntryContainerInitializer").
	 * <p>
	 * 
	 * @param containerPath
	 *            the name of the container, which needs to be resolved
	 * @param project
	 *            a specific project in which the container is being resolved
	 * @return the corresponding container or <code>null</code> if unable to
	 *         find one.
	 * 
	 * @exception CModelException
	 *                if an exception occurred while resolving the container,
	 *                or if the resolved container contains illegal entries
	 *                (contains CDT_CONTAINER entries or null entries).
	 * 
	 * @see PathEntryContainerInitializer
	 * @see IPathEntryContainer
	 * @see #setPathEntryContainer(IPath, ICProject[], IPathEntryContainer,
	 *      IProgressMonitor)
	 */
	public static IPathEntryContainer getPathEntryContainer(IPath containerPath, ICProject project) throws CModelException {
		return pathEntryManager.getPathEntryContainer(containerPath, project);
	}

	/**
	 * Bind a container reference path to some actual containers (<code>IPathEntryContainer</code>).
	 * This API must be invoked whenever changes in container need to be
	 * reflected onto the CModel.
	 * <p>
	 * In reaction to changing container values, the CModel will be updated to
	 * reflect the new state of the updated container.
	 * <p>
	 * This functionality cannot be used while the resource tree is locked.
	 * <p>
	 * PathEntry container values are persisted locally to the workspace, but
	 * are not preserved from a session to another. It is thus highly
	 * recommended to register a <code>PathEntryContainerInitializer</code>
	 * for each referenced container (through the extension point
	 * "org.eclipse.cdt.core.PathEntryContainerInitializer").
	 * <p>
	 * Note: setting a container to <code>null</code> will cause it to be
	 * lazily resolved again whenever its value is required. In particular,
	 * this will cause a registered initializer to be invoked again.
	 * <p>
	 * 
	 * @param affectedProjects -
	 *            the set of projects for which this container is being bound
	 * @param newContainer -
	 *            the container for the affected projects
	 * @param monitor
	 *            a monitor to report progress
	 * @throws CModelException
	 * @see PathEntryContainerInitializer
	 * @see #getPathEntryContainer(IPath, IJavaProject)
	 * @see IPathEntryContainer
	 */
	public static void setPathEntryContainer(ICProject[] affectedProjects, IPathEntryContainer container, IProgressMonitor monitor)
			throws CModelException {
		pathEntryManager.setPathEntryContainer(affectedProjects, container, monitor);
	}

	/**
	 * Helper method use by a pathentry container implementing <code>IPathEntryContainerExtension</code>
	 * It notify the model of changes.
	 * Note: the paths in the <code>PathEntryContainerChanged[]</code> array must be on
	 * source that the container was set too.  If not the changes will be silently ignore.
	 * 
	 * @param container
	 * @param changes array of changes.
	 * @param monitor progress monitor
	 */
	public static void pathEntryContainerUpdates(IPathEntryContainerExtension container, PathEntryContainerChanged[] changes, IProgressMonitor monitor) {
		pathEntryManager.pathEntryContainerUpdates(container, changes, monitor);
	}

	/**
	 * Sets the pathentries of this project using a list of entries.
	 * <p>
	 * Setting the pathentries to <code>null</code> specifies a default
	 * classpath (the project root). Setting the pathentry to an empty array
	 * specifies an empty pathentry.
	 * <p>
	 * 
	 * @param entries
	 *            a list of entries
	 * @param monitor
	 *            the given progress monitor
	 * @exception CModelException
	 *                if the entries could not be set. Reasons include:
	 */
	public static void setRawPathEntries(ICProject cproject, IPathEntry[] newEntries, IProgressMonitor monitor) throws CModelException {
		pathEntryManager.setRawPathEntries(cproject, newEntries, monitor);
	}

	/**
	 * Returns the raw pathentries for the project. This corresponds to the
	 * exact set of entries which were assigned using <code>setRawPathEntries</code>
	 * <p>
	 * 
	 * @return the raw entires for the project
	 * @exception CModelException
	 *                if this element does not exist or if an exception occurs
	 *                while accessing its corresponding resource
	 * @see IPathEntry
	 */
	public static IPathEntry[] getRawPathEntries(ICProject cproject) throws CModelException {
		return pathEntryManager.getRawPathEntries(cproject);
	}

	/**
	 * This method returns the resolved pathentries for the project All
	 * pathEntry.CDT_CONTAINER entries in the project's will be replaced by the
	 * entries they resolve to.
	 * <p>
	 * The resulting resolved entries are accurate for the given point in time.
	 * If the project's raw entries are later modified they can become out of
	 * date. Because of this, hanging on resolved pathentries is not
	 * recommended.
	 * </p>
	 * 
	 * @return the resolved entries for the project
	 * @exception CModelException
	 * @see IPathEntry
	 */
	public static IPathEntry[] getResolvedPathEntries(ICProject cproject) throws CModelException {
		return pathEntryManager.getResolvedPathEntries(cproject);
	}

	/**
	 * This method returns the include entries associated with a translation unit
	 * if the path does not refer to a valid translation unit an empty array is return.
	 * <p>
	 * The resulting resolved entries are accurate for the given point in time.
	 * If the project's raw entries are later modified they can become out of
	 * date. Because of this, hanging on resolved pathentries is not
	 * recommended.
	 * </p>
	 * 
	 * @return the include entries for the translation unit
	 * @exception CModelException
	 * @see IPathEntry
	 */
	public static IIncludeEntry[] getIncludeEntries(IPath path) throws CModelException {
		return pathEntryManager.getIncludeEntries(path);
	}

	/**
	 * This method returns the include file entries associated with a translation unit
	 * if the path does not refer to a valid translation unit an empty array is return.
	 * <p>
	 * The resulting resolved entries are accurate for the given point in time.
	 * If the project's raw entries are later modified they can become out of
	 * date. Because of this, hanging on resolved pathentries is not
	 * recommended.
	 * </p>
	 * 
	 * @return the include file entries for the translation unit
	 * @exception CModelException
	 * @see IPathEntry
	 */
	public static IIncludeFileEntry[] getIncludeFileEntries(IPath path) throws CModelException {
		return pathEntryManager.getIncludeFileEntries(path);
	}

	/**
	 * This method returns the macro entries associated with a translation unit
	 * if the path does not refer to a valid translation unit an empty array is return.
	 * <p>
	 * The resulting resolved entries are accurate for the given point in time.
	 * If the project's raw entries are later modified they can become out of
	 * date. Because of this, hanging on resolved pathentries is not
	 * recommended.
	 * </p>
	 * 
	 * @return the resolved entries for the project
	 * @exception CModelException
	 * @see IPathEntry
	 */
	public static IMacroEntry[] getMacroEntries(IPath path) throws CModelException {
		return pathEntryManager.getMacroEntries(path);
	}

	/**
	 * This method returns the macro file entries associated with a translation unit
	 * if the path does not refer to a valid translation unit an empty array is return.
	 * <p>
	 * The resulting resolved entries are accurate for the given point in time.
	 * If the project's raw entries are later modified they can become out of
	 * date. Because of this, hanging on resolved pathentries is not
	 * recommended.
	 * </p>
	 * 
	 * @return the macro file entries for the translation unit
	 * @exception CModelException
	 * @see IPathEntry
	 */
	public static IMacroFileEntry[] getMacroFileEntries(IPath path) throws CModelException {
		return pathEntryManager.getMacroFileEntries(path);
	}

	/**
	 * Helper method finding the pathentry container initializer registered for
	 * a given container ID or <code>null</code> if none was found while
	 * iterating over the contributions to extension point to the extension
	 * point "org.eclipse.cdt.core.PathEntryContainerInitializer".
	 * <p>
	 * A containerID is the first segment of any container path, used to
	 * identify the registered container initializer.
	 * <p>
	 * 
	 * @param containerID -
	 *            a containerID identifying a registered initializer
	 * @return ClasspathContainerInitializer - the registered classpath
	 *         container initializer or <code>null</code> if none was found.
	 */
	public static PathEntryContainerInitializer getPathEntryContainerInitializer(String containerID) {
		return pathEntryManager.getPathEntryContainerInitializer(containerID);
	}

	/**
	 * Return the IPathEntryStore of the project.
	 * 
	 * @param project
	 * @return
	 * @throws CoreException
	 */
	public static IPathEntryStore getPathEntryStore(IProject project) throws CoreException {
		return pathEntryManager.getPathEntryStore(project, true);
	}

	/**
	 * Set in the map the store, but not persisted.
	 * 
	 * @param project
	 * @param store
	 */
	public static void setPathEntryStore(IProject project, IPathEntryStore store) {
		pathEntryManager.setPathEntryStore(project, store);
	}

	/**
	 * Validate a given path entries for a project, using the following rules:
	 * <ul>
	 *   <li> Entries cannot collide with each other; that is, all entry paths must be unique.
	 *   <li> The output entry location path can be empty, if not they must be located inside the project.
	 *   <li> Source entry location can be null, if not they must be located inside the project,
	 *   <li> A project entry cannot refer to itself directly (that is, a project cannot prerequisite itself).
     *   <li> Source entries or output locations cannot coincidate or be nested in each other, except for the following scenarii listed below:
	 *      <ul><li> A source folder can coincidate with its own output location, in which case this output can then contain library archives. 
	 *                     However, a specific output location cannot coincidate with any library or a distinct source folder than the one referring to it. </li> 
	 *              <li> A source/library folder can be nested in any source folder as long as the nested folder is excluded from the enclosing one. </li>
	 * 			<li> An output location can be nested in a source folder, if the source folder coincidates with the project itself, or if the output
	 * 					location is excluded from the source folder. </li>
	 *      </ul>
	 * </ul>
	 * 
	 *  Note that the entries are not validated automatically. Only bound variables or containers are considered 
	 *  in the checking process (this allows to perform a consistency check on an entry which has references to
	 *  yet non existing projects, folders, ...).
	 *  <p>
	 *  This validation is intended to anticipate issues prior to assigning it to a project. In particular, it will automatically
	 *  be performed during the setting operation (if validation fails, the classpath setting will not complete)
	 *  and during getResolvedPathEntries.
	 *  <p>
	 * @param cProject the given C project
	 * @param PathEntry entries
	 * @return a status object with code <code>IStatus.OK</code> if
	 *		the entries location are compatible, otherwise a status 
	 *		object indicating what is wrong with them
	 */
	public static ICModelStatus validatePathEntries(ICProject cProject, IPathEntry[] entries) {
		return pathEntryManager.validatePathEntry(cProject, entries);
	}

	/**
	 * Returns a C model status describing the problem related to this entry if any, 
	 * a status object with code <code>IStatus.OK</code> if the entry is fine (that is, if the
	 * given entry denotes a valid element).
	 * 
	 * @param cProject the given C project
	 * @param entry the given entry
	 * @param checkSourceAttachment a flag to determine if source attachement should be checked
	 * @param recurseInContainers flag indicating whether validation should be applied to container entries recursively
	 * @return a c model status describing the problem related to this entry if any, a status object with code <code>IStatus.OK</code> if the entry is fine
	 */
	public static ICModelStatus validatePathEntry(ICProject cProject, IPathEntry entry, boolean checkSourceAttachment, boolean recurseInContainers){
		return pathEntryManager.validatePathEntry(cProject, entry, checkSourceAttachment, recurseInContainers);
	}

	/**
	 * Return the singleton.
	 */
	public static CoreModel getDefault() {
		if (cmodel == null) {
			cmodel = new CoreModel();
		}
		return cmodel;
	}

	public void addElementChangedListener(IElementChangedListener listener) {
		manager.addElementChangedListener(listener);
	}

	/**
	 * Removes the given element changed listener. Has no affect if an
	 * identical listener is not registered.
	 * 
	 * @param listener
	 *            the listener
	 */
	public void removeElementChangedListener(IElementChangedListener listener) {
		manager.removeElementChangedListener(listener);
	}

	/**
	 * @see Plugin#startup
	 */
	public void startup() {
		manager.startup();
	}

	public void shutdown() {
		manager.shutdown();
	}

	private CoreModel() {
	}

	
	/**
	 * Runs the given action as an atomic C model operation.
	 * <p>
	 * After running a method that modifies C elements,
	 * registered listeners receive after-the-fact notification of
	 * what just transpired, in the form of a element changed event.
	 * This method allows clients to call a number of
	 * methods that modify C elements and only have element
	 * changed event notifications reported at the end of the entire
	 * batch.
	 * </p>
	 * <p>
	 * If this method is called outside the dynamic scope of another such
	 * call, this method runs the action and then reports a single
	 * element changed event describing the net effect of all changes
	 * done to C elements by the action.
	 * </p>
	 * <p>
	 * If this method is called in the dynamic scope of another such
	 * call, this method simply runs the action.
	 * </p>
	 *
	 * @param action the action to perform
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 *    reporting and cancellation are not desired
	 * @exception CoreException if the operation failed.
	 * @since 2.1
	 */
	public static void run(IWorkspaceRunnable action, IProgressMonitor monitor) throws CoreException {
		run(action, ResourcesPlugin.getWorkspace().getRoot(), monitor);
	}
	/**
	 * Runs the given action as an atomic C model operation.
	 * <p>
	 * After running a method that modifies C elements,
	 * registered listeners receive after-the-fact notification of
	 * what just transpired, in the form of a element changed event.
	 * This method allows clients to call a number of
	 * methods that modify C elements and only have element
	 * changed event notifications reported at the end of the entire
	 * batch.
	 * </p>
	 * <p>
	 * If this method is called outside the dynamic scope of another such
	 * call, this method runs the action and then reports a single
	 * element changed event describing the net effect of all changes
	 * done to C elements by the action.
	 * </p>
	 * <p>
	 * If this method is called in the dynamic scope of another such
	 * call, this method simply runs the action.
	 * </p>
	 * <p>
 	 * The supplied scheduling rule is used to determine whether this operation can be
	 * run simultaneously with workspace changes in other threads. See 
	 * <code>IWorkspace.run(...)</code> for more details.
 	 * </p>
	 *
	 * @param action the action to perform
	 * @param rule the scheduling rule to use when running this operation, or
	 * <code>null</code> if there are no scheduling restrictions for this operation.
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 *    reporting and cancellation are not desired
	 * @exception CoreException if the operation failed.
	 * @since 3.0
	 */
	public static void run(IWorkspaceRunnable action, ISchedulingRule rule, IProgressMonitor monitor) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace.isTreeLocked()) {
			new BatchOperation(action).run(monitor);
		} else {
			// use IWorkspace.run(...) to ensure that a build will be done in autobuild mode
			workspace.run(new BatchOperation(action), rule, IWorkspace.AVOID_UPDATE, monitor);
		}
	}	
	
	/**
	 * The method returns whether scanner information for a resource is empty or not.
	 * <p>
	 * Although this looks like IScannerInfoProvider method, eventually this interface
	 * will be deprecated and the service will be moved to CoreModel.
	 * </p>
	 * 
	 * @param resource
	 * @since 3.0
	 */
	public static boolean isScannerInformationEmpty(IResource resource) {
		IProject project = resource.getProject();
		CProjectDescriptionManager mngr = CProjectDescriptionManager.getInstance(); 
		ICProjectDescription des = mngr.getProjectDescription(project, false);
		if(des != null){
			ICConfigurationDescription indexCfg = des.getDefaultSettingConfiguration();
			if(indexCfg != null){
				if(!mngr.isNewStyleCfg(indexCfg)){
					return oldIsScannerInformationEmpty(resource);
				}
				ICLanguageSetting lSetting = indexCfg.getLanguageSettingForFile(resource.getProjectRelativePath(), false);
				if(lSetting != null && lSetting instanceof CLanguageSettingCache){
					if(!((CLanguageSettingCache)lSetting).containsDiscoveredScannerInfo())
						lSetting = null;
				}
				if(lSetting != null){
					ICLanguageSettingEntry[] entries = lSetting.getSettingEntries(ICLanguageSettingEntry.INCLUDE_PATH);
					if(entries.length != 0)
						return false;
					
					entries = lSetting.getSettingEntries(ICLanguageSettingEntry.MACRO);
					if(entries.length != 0)
						return false;

					entries = lSetting.getSettingEntries(ICLanguageSettingEntry.INCLUDE_FILE);
					if(entries.length != 0)
						return false;
					
					entries = lSetting.getSettingEntries(ICLanguageSettingEntry.MACRO_FILE);
					if(entries.length != 0)
						return false;
				}
			}
		}
		return true;
	}
	
	private static boolean oldIsScannerInformationEmpty(IResource resource) {
		final int PATH_ENTRY_MASK = IPathEntry.CDT_INCLUDE | IPathEntry.CDT_MACRO |
									IPathEntry.CDT_INCLUDE_FILE | IPathEntry.CDT_MACRO_FILE;
		boolean rc = true;
		IPath resPath = resource.getFullPath();
		IProject project = resource.getProject();
		
		ICProject cProject = CoreModel.getDefault().create(project);
		if (cProject != null) {
			try {
				IPathEntry[] resolvedPE = CoreModel.getRawPathEntries(cProject);
				for (int i = 0; i < resolvedPE.length; i++) {
					IPathEntry pe = resolvedPE[i];
					// first check all containers
					if (pe.getEntryKind() == IPathEntry.CDT_CONTAINER) {
						IPathEntryContainer peContainer = CoreModel.getPathEntryContainer(
								pe.getPath(), cProject);
						if (peContainer != null) {
							if (peContainer instanceof IPathEntryContainerExtension) {
								IPathEntryContainerExtension contExt = (IPathEntryContainerExtension) peContainer;
								if (!contExt.isEmpty(resPath)) {
									rc = false;
									break;
								}
							}
							else if (peContainer.getPathEntries().length > 0) {
								rc = false;
								break;
							}
						}
					}
					// then the user specified scanner info
					else if ((pe.getEntryKind() & PATH_ENTRY_MASK) != 0) {
						IPath affectedPath = pe.getPath();
						if (affectedPath.isPrefixOf(resource.getFullPath())) {
							rc = false;
							break;
						}
					}
				}
			} catch (CModelException e) {
			} 
		}
		return rc;
	}
	
	/**
	 * this method is a full equivalent to {@link #createProjectDescription(IProject, boolean, false)}
	 * 
	 * @see #createProjectDescription(IProject, boolean, boolean)
	 */
	public ICProjectDescription createProjectDescription(IProject project, boolean loadIfExists) throws CoreException{
		return descriptionManager.createProjectDescription(project, loadIfExists);
	}
	
	/**
	 * the method creates and returns a writable project description
	 * 
	 * @param project project for which the project description is requested
	 * @param loadIfExists if true the method first tries to load and return the project description
	 * from the settings file (.cproject)
	 * if false, the stored settings are ignored and the new (empty) project description is created
	 * @param creating if true the created project description will be contain the true "isCdtProjectCreating" state.
	 * NOTE: in case the project already contains the project description AND its "isCdtProjectCreating" is false
	 * the resulting description will be created with the false "isCdtProjectCreating" state
	 * 
	 * NOTE: changes made to the returned project description will not be applied until the {@link #setProjectDescription(IProject, ICProjectDescription)} is called 
	 * @return {@link ICProjectDescription}
	 * @throws CoreException
	 */
	public ICProjectDescription createProjectDescription(IProject project, boolean loadIfExists, boolean creating) throws CoreException{
		return descriptionManager.createProjectDescription(project, loadIfExists, creating);
	}
	
	/**
	 * returns the project description associated with this project
	 * this is a convenience method fully equivalent to getProjectDescription(project, true)
	 * see {@link #getProjectDescription(IProject, boolean)} for more detail
	 * @param project
	 * @return a writable copy of the ICProjectDescription or null if the project does not contain the
	 * CDT data associated with it. 
	 * Note: changes to the project description will not be reflected/used by the core
	 * untill the {@link #setProjectDescription(IProject, ICProjectDescription)} is called
	 * 
	 * @see #getProjectDescription(IProject, boolean)
	 */
	public ICProjectDescription getProjectDescription(IProject project){
		return descriptionManager.getProjectDescription(project);
	}
	
	/**
	 * this method is called to save/apply the project description
	 * the method should be called to apply changes made to the project description
	 * returned by the {@link #getProjectDescription(IProject, boolean)} or {@link #createProjectDescription(IProject, boolean)} 
	 * 
	 * @param project
	 * @param des
	 * @throws CoreException
	 * 
	 * @see {@link #getProjectDescription(IProject, boolean)}
	 * @see #createProjectDescription(IProject, boolean)
	 */
	public void setProjectDescription(IProject project, ICProjectDescription des) throws CoreException {
		descriptionManager.setProjectDescription(project, des);
	}

	public void setProjectDescription(IProject project, ICProjectDescription des, boolean force, IProgressMonitor monitor) throws CoreException {
		descriptionManager.setProjectDescription(project, des, force, monitor);
	}

	/**
	 * returns the project description associated with this project
	 * 
	 * @param project project for which the description is requested
	 * @param write if true, the writable description copy is returned. 
	 * If false the cached read-only description is returned.
	 * 
	 * CDT core maintains the cached project description settings. If only read access is needed to description,
	 * then the read-only project description should be obtained.
	 * This description always operates with cached data and thus it is better to use it for performance reasons
	 * All set* calls to the read-only description result in the {@link WriteAccessException}
	 * 
	 * When the writable description is requested, the description copy is created.
	 * Changes to this description will not be reflected/used by the core and Build System untill the
	 * {@link #setProjectDescription(IProject, ICProjectDescription)} is called
	 *
	 * Each getProjectDescription(project, true) returns a new copy of the project description 
	 * 
	 * The writable description uses the cached data untill the first set call
	 * after that the description communicates directly to the Build System
	 * i.e. the implementer of the org.eclipse.cdt.core.CConfigurationDataProvider extension
	 * This ensures the Core<->Build System settings integrity
	 * 
	 * @return {@link ICProjectDescription}
	 */
	public ICProjectDescription getProjectDescription(IProject project, boolean write){
		return descriptionManager.getProjectDescription(project, write);
	}
	
	/**
	 * forces the cached data of the specified projects to be re-calculated.
	 * if the <code>projects</code> argument is <code>null</code> al projects 
	 * within the workspace are updated
	 * 
	 * @param projects
	 * @param monitor
	 * @throws CoreException 
	 */
	public void updateProjectDescriptions(IProject projects[], IProgressMonitor monitor) throws CoreException{
		descriptionManager.updateProjectDescriptions(projects, monitor);
	}
	
	/**
	 * aswers whether the given project is a new-style project, i.e. CConfigurationDataProvider-driven
	 * @param project
	 * @return
	 */
	public boolean isNewStyleProject(IProject project){
		return descriptionManager.isNewStyleProject(project);
	}

	/**
	 * aswers whether the given project is a new-style project, i.e. CConfigurationDataProvider-driven
	 * @param des
	 * @return
	 */
	public boolean isNewStyleProject(ICProjectDescription des){
		return descriptionManager.isNewStyleProject(des);
	}
	
	public void addCProjectDescriptionListener(ICProjectDescriptionListener listener, int eventTypes){
		descriptionManager.addCProjectDescriptionListener(listener, eventTypes);
	}

	public void removeCProjectDescriptionListener(ICProjectDescriptionListener listener){
		descriptionManager.removeCProjectDescriptionListener(listener);
	}
	
	public ICProjectDescriptionManager getProjectDescriptionManager(){
		return descriptionManager;
	}
}
