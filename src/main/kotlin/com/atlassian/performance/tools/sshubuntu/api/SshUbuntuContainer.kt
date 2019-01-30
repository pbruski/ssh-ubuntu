package com.atlassian.performance.tools.sshubuntu.api

import com.atlassian.performance.tools.sshubuntu.docker.Ryuk
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.io.File

class SshUbuntuContainer {
    internal companion object {
        private val SSH_PORT = 22
    }

    fun start(): SshUbuntu {
        Ryuk.disable()
        val ubuntu: GenericContainerImpl = GenericContainerImpl("rastasheep/ubuntu-sshd:18.04")
            .withExposedPorts(SSH_PORT)
            .waitingFor(Wait.forListeningPort())

        ubuntu.start()
        val authorizedKeys = MountableFile.forClasspathResource("authorized_keys")
        val sshKey = MountableFile.forClasspathResource("ssh_key")
        ubuntu.copyFileToContainer(authorizedKeys, "/root/.ssh/authorized_keys")
        val sshPort = getHostSshPort(ubuntu)
        val privateKey = File(sshKey.filesystemPath).toPath()
        val ipAddress = ubuntu.containerIpAddress
        val sshHost = SshHost(
            ipAddress = ipAddress,
            userName = "root",
            port = sshPort,
            privateKey = privateKey
        )
        return object : SshUbuntu {
            override fun getSsh(): SshHost {
                return sshHost
            }

            override fun close() {
                ubuntu.close()
            }
        }
    }

    private fun getHostSshPort(ubuntuContainer: GenericContainerImpl) =
        ubuntuContainer.getMappedPort(SSH_PORT)

    /**
     * TestContainers depends on construction of recursive generic types like class C<SELF extends C<SELF>>. It doesn't work
     * in kotlin. See:
     * https://youtrack.jetbrains.com/issue/KT-17186
     * https://github.com/testcontainers/testcontainers-java/issues/318
     * The class is a workaround for the problem.
     */
    private class GenericContainerImpl(dockerImageName: String) : GenericContainer<GenericContainerImpl>(dockerImageName) {
        override fun getLivenessCheckPortNumbers(): Set<Int> {
            return setOf(getMappedPort(SSH_PORT))
        }
    }
}