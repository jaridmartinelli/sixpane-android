import com.sixpane.app.WorkspacePolicy
fun main(){
    check(WorkspacePolicy.canCreate(6))
    check(!WorkspacePolicy.canCreate(7))
    check(!WorkspacePolicy.canClose(true))
    check(!WorkspacePolicy.canReset(true))
    check(!WorkspacePolicy.canChangeDesktop(true))
    check(!WorkspacePolicy.canModifyCookies(true))
    check(WorkspacePolicy.canClose(false))
    check(WorkspacePolicy.zoom(100,1)==110)
    check(WorkspacePolicy.zoom(100,-1)==90)
    check(WorkspacePolicy.zoom(200,1)==200)
    check(WorkspacePolicy.zoom(50,-1)==50)
    check(WorkspacePolicy.displayZoom(100)=="")
    check(WorkspacePolicy.displayZoom(110)=="110%")
    check(WorkspacePolicy.normalViewScale(98)==.98f)
    println("SIXPANE policy assertions passed (JVM, not Android device)")
}
