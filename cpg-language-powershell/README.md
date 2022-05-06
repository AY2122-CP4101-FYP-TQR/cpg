# PowerShell for CPG
Extension of Fraunhofer-AISEC/cpg supported language to include PowerShell scripts.
The AST of the PS script is obtained via `convertAstJson.ps1` file and passed as JSON object
to the cpg library to handle.  

**NOTE:**  
To produce the CPG on Neo4j, it must be executed while being in `cpg-language-powershell` directory.

### Completed
The features implemented are able to handle most simple cases and may have difficulty with more complex constructs.
- [x] Variable Declaration/Assignment
- [x] Simple Array 
- [x] Function Declaration & parameters
- [x] Function call & arguments
- [x] Cmdlet call & arguments
- [x] If, For, While, DoWhile, ForEach
- [x] DoUntil
  - Same as DoWhile but with inverted conditions
- [x] Switch
- [x] Try and Catch
- [x] Break and Continue

- [x] Simple ScriptBlock
- [x] Simple Pipeline 
- [x] Simple MemberCall Invocation 
  - E.g. "a".Invoke()

### ToDo  
- [ ] Improve Pipeline
  - Linking one pipe's output to another's input.
  - Able more complex pipelines
- [ ] Improve ScriptBlock
- [ ] Improve MemberCall Invocation

### Areas identified that are not Implemented
This list contains features identified that are not implemented due to resource constraints like time.
- [ ] Classes
- [ ] Namespace
  - Current implementation uses a naive global namespace with script blocks acting as another namespace.
- [ ] Hash tables
- [ ] Function attributes & return
- [ ] ...
